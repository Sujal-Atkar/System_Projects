package com.system_project.distributedpaymentsystem.service.recovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.config.GeminiRecoveryConfig;
import com.system_project.distributedpaymentsystem.domain.entity.DecisionType;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk;
import com.system_project.distributedpaymentsystem.repository.RecoveryDecisionRepository;

@Service
public class RecoveryDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryDecisionService.class);

    private final GeminiRecoveryDecisionClient geminiClient;
    private final RecoveryDecisionRepository recoveryDecisionRepository;
    private final GeminiRecoveryConfig config;

    public RecoveryDecisionService(GeminiRecoveryDecisionClient geminiClient,
            RecoveryDecisionRepository recoveryDecisionRepository,
            GeminiRecoveryConfig config) {
        this.geminiClient = geminiClient;
        this.recoveryDecisionRepository = recoveryDecisionRepository;
        this.config = config;
    }

    /**
     * Decide recovery action using Gemini AI.
     *
     * @param risk the revenue risk
     * @param policyResult the policy evaluation result
     * @param latestAttempt the latest payment attempt with failure info
     * @param provider the provider involved
     * @return persisted RecoveryDecision
     */
    @Transactional
    public RecoveryDecision decideRecoveryAction(RevenueRisk risk, RecoveryPolicyResult policyResult,
            PaymentAttempt latestAttempt, Provider provider) {

        // Check if already decided
        Optional<RecoveryDecision> existing = recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId());
        if (existing.isPresent()) {
            log.info("Recovery decision already exists for risk {}", risk.getRiskId());
            return existing.get();
        }

        // If policy allows only NO_ACTION, don't call Gemini
        if (!policyResult.isRetryAllowed() && !policyResult.isRerouteAllowed()) {
            RecoveryDecision noActionDecision = new RecoveryDecision(
                    UUID.randomUUID(),
                    risk,
                    DecisionType.NO_ACTION,
                    "No eligible executable recovery action was available.",
                    null,
                    config.getModel(),
                    true,
                    OffsetDateTime.now()
            );
            return recoveryDecisionRepository.save(noActionDecision);
        }

        // Build allowed actions list based on policy
        List<String> allowedActions = buildAllowedActions(policyResult);

        // Build AI recovery context
        AIRecoveryContext context = buildContext(risk, policyResult, latestAttempt, provider, allowedActions);

        // Call Gemini to decide
        GeminiDecisionResult geminiResult = geminiClient.requestDecision(context);

        // Determine final decision
        DecisionType finalDecision;
        String finalReasoning;
        BigDecimal finalConfidence;

        if (geminiResult.isValid()) {
            // Validate decision is in allowed actions
            try {
                finalDecision = DecisionType.valueOf(geminiResult.getDecision());
                if (!isActionAllowed(finalDecision, policyResult)) {
                    log.warn("Gemini decision not allowed by policy, using fallback");
                    finalDecision = DecisionType.valueOf(policyResult.getRecommendedAction().name());
                    finalReasoning = "Fallback: Gemini decision invalid. Using policy recommendation.";
                    finalConfidence = null;
                } else {
                    finalReasoning = geminiResult.getReasoning();
                    finalConfidence = geminiResult.getConfidence();
                }
            } catch (IllegalArgumentException e) {
                log.warn("Gemini returned invalid decision type: {}", geminiResult.getDecision());
                finalDecision = DecisionType.valueOf(policyResult.getRecommendedAction().name());
                finalReasoning = "Fallback: Invalid decision type. Using policy recommendation.";
                finalConfidence = null;
            }
        } else {
            // Gemini failed; use policy recommendation
            log.warn("Gemini call failed: {}. Using policy recommendation.", geminiResult.getErrorMessage());
            finalDecision = DecisionType.valueOf(policyResult.getRecommendedAction().name());
            finalReasoning = "Fallback: " + geminiResult.getErrorMessage();
            finalConfidence = null;
        }

        // Persist decision
        RecoveryDecision decision = new RecoveryDecision(
                UUID.randomUUID(),
                risk,
                finalDecision,
                finalReasoning,
                finalConfidence,
                config.getModel(),
                true,
                OffsetDateTime.now()
        );

        RecoveryDecision savedDecision = recoveryDecisionRepository.save(decision);
        log.info("RecoveryDecision persisted: {} for risk {} with decision {}",
                savedDecision.getDecisionId(), risk.getRiskId(), finalDecision);

        return savedDecision;
    }

    private List<String> buildAllowedActions(RecoveryPolicyResult policyResult) {
        List<String> allowed = new java.util.ArrayList<>();
        if (policyResult.isRetryAllowed()) {
            allowed.add("RETRY");
        }
        if (policyResult.isRerouteAllowed()) {
            allowed.add("REROUTE");
        }
        allowed.add("NOTIFY");
        allowed.add("NO_ACTION");
        return allowed;
    }

    private boolean isActionAllowed(DecisionType decision, RecoveryPolicyResult policyResult) {
        if (decision == DecisionType.RETRY) {
            return policyResult.isRetryAllowed();
        }
        if (decision == DecisionType.REROUTE) {
            return policyResult.isRerouteAllowed();
        }
        if (decision == DecisionType.NOTIFY || decision == DecisionType.NO_ACTION) {
            return true;
        }
        return false;
    }

    private AIRecoveryContext buildContext(RevenueRisk risk, RecoveryPolicyResult policyResult,
            PaymentAttempt latestAttempt, Provider provider, List<String> allowedActions) {

        return new AIRecoveryContext(
                risk.getRiskAmount(),
                risk.getCurrency(),
                latestAttempt.getFailureReason() != null ? latestAttempt.getFailureReason() : "Unknown",
                policyResult.getReason().name(),
                provider.getProviderCode(),
                latestAttempt.getAttemptNumber(),
                allowedActions,
                risk.getRiskScore()
        );
    }
}
