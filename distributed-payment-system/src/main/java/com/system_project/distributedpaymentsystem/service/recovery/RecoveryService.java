package com.system_project.distributedpaymentsystem.service.recovery;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.DecisionType;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCase;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryActionStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.repository.RecoveryActionRepository;

@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryExecutor recoveryExecutor;
    private final RecoveryPolicy recoveryPolicy;
    private final RevenueRiskService revenueRiskService;
    private final RecoveryDecisionService recoveryDecisionService;

    public RecoveryService(PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            RecoveryActionRepository recoveryActionRepository,
            RecoveryExecutor recoveryExecutor,
            RecoveryPolicy recoveryPolicy,
            RevenueRiskService revenueRiskService,
            RecoveryDecisionService recoveryDecisionService) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryExecutor = recoveryExecutor;
        this.recoveryPolicy = recoveryPolicy;
        this.revenueRiskService = revenueRiskService;
        this.recoveryDecisionService = recoveryDecisionService;
    }

    @Transactional
    public RecoveryAction processFailedPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            log.info("Recovery blocked for UNKNOWN payment {}", paymentId);
            return null;
        }

        if (payment.getStatus() != PaymentStatus.FAILED) {
            log.info("Recovery ignored for payment {} because status is {}", paymentId, payment.getStatus());
            return null;
        }

        // Create RevenueRisk in DETECTED state if this is a definitive failure
        Optional<RevenueRisk> riskOpt = revenueRiskService.createIfAbsent(payment);
        if (riskOpt.isEmpty()) {
            log.info("RevenueRisk could not be created for payment {} (ineligible payment status)", paymentId);
            return null;
        }

        if (hasOpenReconciliationCase(payment)) {
            log.info("Recovery blocked for payment {} due to open reconciliation case", paymentId);
            return null;
        }

        List<PaymentAttempt> attempts = paymentAttemptRepository
                .findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId());
        PaymentAttempt latestAttempt = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Provider provider = latestAttempt == null ? null : latestAttempt.getProvider();

        if (provider == null) {
            log.info("Recovery blocked for payment {} because no provider attempt exists", paymentId);
            return null;
        }

        ProviderProcessingResult failureResult = latestAttempt.getErrorCode() == null
                ? ProviderProcessingResult.failed(provider.getProviderCode(), "TECHNICAL_FAILURE", latestAttempt.getFailureReason())
                : ProviderProcessingResult.failed(
                        latestAttempt.getProviderReference(),
                        latestAttempt.getErrorCode(),
                        latestAttempt.getFailureReason());

        RecoveryPolicyResult policyResult = recoveryPolicy.evaluate(payment, provider, failureResult);
        if (policyResult == null || policyResult.isBlocked()) {
            log.info("Recovery policy blocked action for payment {}: {}", paymentId, policyResult == null ? "no policy result" : policyResult.getReason());
            return null;
        }

        // Attempt atomic claim of the RevenueRisk
        RevenueRisk risk = riskOpt.get();
        ClaimResult claimResult = revenueRiskService.claimForDecision(risk.getRiskId());
        if (!claimResult.isClaimed()) {
            log.info("RevenueRisk claim failed for payment {} (likely redelivery): {}", paymentId, claimResult.getReason());
            return null;
        }

        // Call AI decision service to get the recovery decision
        RecoveryDecision recoveryDecision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, latestAttempt, provider);
        DecisionType aiDecision = recoveryDecision.getDecisionType();

        // If NO_ACTION was decided, don't create a RecoveryAction
        if (aiDecision == DecisionType.NO_ACTION) {
            log.info("AI decision is NO_ACTION for payment {}", paymentId);
            return null;
        }

        // Map DecisionType to RecoveryActionType
        RecoveryActionType actionType = RecoveryActionType.valueOf(aiDecision.name());

        // Check for existing action
        Optional<RecoveryAction> existing = recoveryActionRepository
                .findTopByPayment_PaymentIdAndActionTypeAndStatusInOrderByAttemptNumberDesc(
                        payment.getPaymentId(),
                        actionType,
                        List.of(RecoveryActionStatus.PENDING, RecoveryActionStatus.EXECUTING, RecoveryActionStatus.SUCCESS, RecoveryActionStatus.FAILED));
        if (existing.isPresent()) {
            RecoveryAction current = existing.get();
            if (current.getStatus() == RecoveryActionStatus.EXECUTING || current.getStatus() == RecoveryActionStatus.PENDING) {
                log.info("Recovery action already exists for payment {} and action type {}", paymentId, actionType);
                return current;
            }
        }

        // Create RecoveryAction based on AI decision
        RecoveryAction action = new RecoveryAction(
                UUID.randomUUID(),
                recoveryDecision.getDecisionId(),
                payment,
                risk.getRiskId(),
                actionType,
                provider,
                RecoveryActionStatus.PENDING,
                attempts.size() + 1,
                null,
                null,
                failureResult.getFailureReason()
        );

        RecoveryAction savedAction = recoveryActionRepository.save(action);

        // Execute the action if allowed by policy
        if ((actionType == RecoveryActionType.RETRY && policyResult.isRetryAllowed()) ||
            (actionType == RecoveryActionType.REROUTE && policyResult.isRerouteAllowed())) {
            recoveryExecutor.execute(savedAction.getActionId());
        }

        return savedAction;
    }

    private boolean hasOpenReconciliationCase(Payment payment) {
        if (payment == null) {
            return false;
        }
        return reconciliationCaseRepository
                .findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(payment.getPaymentId(), ReconciliationCaseStatus.OPEN)
                .isPresent();
    }
}
