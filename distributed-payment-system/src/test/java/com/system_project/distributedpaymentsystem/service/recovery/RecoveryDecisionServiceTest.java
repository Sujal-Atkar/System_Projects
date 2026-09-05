package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.system_project.distributedpaymentsystem.config.GeminiRecoveryConfig;
import com.system_project.distributedpaymentsystem.domain.entity.DecisionType;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttemptStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentType;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ProviderStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRiskStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RiskType;
import com.system_project.distributedpaymentsystem.repository.RecoveryDecisionRepository;

@ExtendWith(MockitoExtension.class)
class RecoveryDecisionServiceTest {

    @Mock
    private GeminiRecoveryDecisionClient geminiClient;

    @Mock
    private RecoveryDecisionRepository recoveryDecisionRepository;

    @Mock
    private GeminiRecoveryConfig config;

    private RecoveryDecisionService recoveryDecisionService;

    @BeforeEach
    void setUp() {
        recoveryDecisionService = new RecoveryDecisionService(geminiClient, recoveryDecisionRepository, config);
    }

    @Test
    void shouldAcceptAllowedRetryDecision() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        GeminiDecisionResult geminiResult = GeminiDecisionResult.success(
                "RETRY", "Retry is viable option", BigDecimal.valueOf(0.9)
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.RETRY, decision.getDecisionType());
        assertEquals("Retry is viable option", decision.getReasoning());
        assertEquals(BigDecimal.valueOf(0.9), decision.getConfidenceScore());
        verify(geminiClient).requestDecision(any(AIRecoveryContext.class));
        verify(recoveryDecisionRepository).save(any(RecoveryDecision.class));
    }

    @Test
    void shouldAcceptAllowedRerouteDecision() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.PROVIDER_UNAVAILABLE,
                RecoveryActionType.REROUTE, false, true, false
        );

        GeminiDecisionResult geminiResult = GeminiDecisionResult.success(
                "REROUTE", "Reroute to alternative provider", BigDecimal.valueOf(0.85)
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.REROUTE, decision.getDecisionType());
        verify(geminiClient).requestDecision(any(AIRecoveryContext.class));
        verify(recoveryDecisionRepository).save(any(RecoveryDecision.class));
    }

    @Test
    void shouldAcceptNotifyDecision() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        // Policy allows RETRY so Gemini will be called
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        GeminiDecisionResult geminiResult = GeminiDecisionResult.success(
                "NOTIFY", "Notify merchant of payment failure", BigDecimal.valueOf(0.8)
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.NOTIFY, decision.getDecisionType());
        verify(geminiClient).requestDecision(any(AIRecoveryContext.class));
        verify(recoveryDecisionRepository).save(any(RecoveryDecision.class));
    }

    @Test
    void shouldRejectDisallowedDecisionAndFallback() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        // Gemini returns REROUTE but policy only allows RETRY
        GeminiDecisionResult geminiResult = GeminiDecisionResult.success(
                "REROUTE", "Reroute is better", BigDecimal.valueOf(0.9)
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.RETRY, decision.getDecisionType());
        assertEquals("Fallback: Gemini decision invalid. Using policy recommendation.", decision.getReasoning());
    }

    @Test
    void shouldFallbackWhenGeminiReturnsInvalidDecisionType() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        // Gemini returns invalid decision
        GeminiDecisionResult geminiResult = GeminiDecisionResult.success(
                "INVALID_ACTION", "Invalid action", BigDecimal.valueOf(0.5)
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.RETRY, decision.getDecisionType());
        assertEquals("Fallback: Invalid decision type. Using policy recommendation.", decision.getReasoning());
    }

    @Test
    void shouldFallbackWhenGeminiCallFails() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        GeminiDecisionResult geminiResult = GeminiDecisionResult.failure("Gemini API timeout");

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.RETRY, decision.getDecisionType());
        assertEquals("Fallback: Gemini API timeout", decision.getReasoning());
    }

    @Test
    void shouldNotCallGeminiWhenOnlyNoActionAllowed() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.UNKNOWN,
                RecoveryActionType.NO_ACTION, false, false, false
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(DecisionType.NO_ACTION, decision.getDecisionType());
        verify(geminiClient, never()).requestDecision(any(AIRecoveryContext.class));
        verify(recoveryDecisionRepository).save(any(RecoveryDecision.class));
    }

    @Test
    void shouldReturnExistingDecisionIfAlreadyCreated() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.IN_RECOVERY);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryDecision existingDecision = new RecoveryDecision(
                UUID.randomUUID(), risk, DecisionType.RETRY, "Previous decision", BigDecimal.valueOf(0.9),
                "gemini-2.0-flash", true, OffsetDateTime.now()
        );
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.of(existingDecision));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals(existingDecision.getDecisionId(), decision.getDecisionId());
        verify(geminiClient, never()).requestDecision(any(AIRecoveryContext.class));
        verify(recoveryDecisionRepository, never()).save(any(RecoveryDecision.class));
    }

    @Test
    void shouldPersistDecisionWithModelVersionAndBoundedFlag() {
        RevenueRisk risk = createRevenueRisk(RevenueRiskStatus.DETECTED);
        PaymentAttempt attempt = createPaymentAttempt(1);
        Provider provider = createProvider();
        RecoveryPolicyResult policyResult = new RecoveryPolicyResult(
                RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false
        );

        GeminiDecisionResult geminiResult = GeminiDecisionResult.success(
                "RETRY", "Retry is viable", BigDecimal.valueOf(0.95)
        );

        when(recoveryDecisionRepository.findByRisk_RiskId(risk.getRiskId())).thenReturn(Optional.empty());
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(geminiClient.requestDecision(any(AIRecoveryContext.class))).thenReturn(geminiResult);
        when(recoveryDecisionRepository.save(any(RecoveryDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryDecision decision = recoveryDecisionService.decideRecoveryAction(risk, policyResult, attempt, provider);

        assertNotNull(decision);
        assertEquals("gemini-2.0-flash", decision.getModelVersion());
        assertEquals(true, decision.getBounded());
        verify(recoveryDecisionRepository).save(any(RecoveryDecision.class));
    }

    private RevenueRisk createRevenueRisk(RevenueRiskStatus status) {
        Merchant merchant = new Merchant(
                UUID.randomUUID(), "Test Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null
        );
        Payment payment = new Payment(
                UUID.randomUUID(), merchant, BigDecimal.valueOf(100), "USD",
                PaymentStatus.FAILED, "idempotency-key", "customer-ref", "order-ref",
                OffsetDateTime.now(), OffsetDateTime.now(), PaymentType.PAYMENT, "description"
        );
        return new RevenueRisk(
                UUID.randomUUID(), merchant, payment, RiskType.PAYMENT_FAILURE,
                BigDecimal.valueOf(100), "USD", BigDecimal.valueOf(1.0), "test",
                status, OffsetDateTime.now(), null
        );
    }

    private PaymentAttempt createPaymentAttempt(int attemptNumber) {
        Merchant merchant = new Merchant(
                UUID.randomUUID(), "Test Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null
        );
        Payment payment = new Payment(
                UUID.randomUUID(), merchant, BigDecimal.valueOf(100), "USD",
                PaymentStatus.FAILED, "idempotency-key", "customer-ref", "order-ref",
                OffsetDateTime.now(), OffsetDateTime.now(), PaymentType.PAYMENT, "description"
        );
        Provider provider = createProvider();
        return new PaymentAttempt(
                UUID.randomUUID(), payment, provider, attemptNumber,
                BigDecimal.valueOf(100), "USD", PaymentAttemptStatus.FAILED,
                "provider-ref", "error-code", "Transient failure",
                new java.util.HashMap<>(), new java.util.HashMap<>(), 100,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    private Provider createProvider() {
        return new Provider(
                UUID.randomUUID(), "Primary Provider", "primary-provider",
                1, ProviderStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now()
        );
    }
}
