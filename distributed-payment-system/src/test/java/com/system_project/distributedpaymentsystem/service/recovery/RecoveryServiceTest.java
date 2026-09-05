package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttemptStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentType;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ProviderStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryActionStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResult;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResultOutcome;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.repository.RecoveryActionRepository;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private ReconciliationCaseRepository reconciliationCaseRepository;

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @Mock
    private RecoveryExecutor recoveryExecutor;

    @Mock
    private RecoveryPolicy recoveryPolicy;

    @Mock
    private RevenueRiskService revenueRiskService;

    @Mock
    private RecoveryDecisionService recoveryDecisionService;

    private RecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new RecoveryService(paymentRepository, paymentAttemptRepository,
                reconciliationCaseRepository, recoveryActionRepository, recoveryExecutor, recoveryPolicy, revenueRiskService, recoveryDecisionService);
    }

    @Test
    void shouldCreateRetryRecoveryActionForFailedPayment() {
        Payment payment = createPayment(PaymentStatus.FAILED);
        Provider provider = createProvider("Primary");
        PaymentAttempt failedAttempt = createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1);
        com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk risk = new com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk(
                UUID.randomUUID(), payment.getMerchant(), payment,
                com.system_project.distributedpaymentsystem.domain.entity.RiskType.PAYMENT_FAILURE,
                payment.getAmount(), payment.getCurrency(), BigDecimal.valueOf(1.0),
                "test", com.system_project.distributedpaymentsystem.domain.entity.RevenueRiskStatus.DETECTED,
                OffsetDateTime.now(), null
        );
        com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision decision = new com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision(
                UUID.randomUUID(), risk, com.system_project.distributedpaymentsystem.domain.entity.DecisionType.RETRY,
                "Retry is eligible", BigDecimal.valueOf(0.95), "gemini-2.0-flash", true, OffsetDateTime.now()
        );

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(revenueRiskService.createIfAbsent(payment)).thenReturn(Optional.of(risk));
        when(revenueRiskService.claimForDecision(risk.getRiskId())).thenReturn(ClaimResult.success());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(failedAttempt));
        when(recoveryPolicy.evaluate(eq(payment), eq(provider), any(ProviderProcessingResult.class)))
                .thenReturn(new RecoveryPolicyResult(RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                        RecoveryActionType.RETRY, true, false, false));
        when(recoveryDecisionService.decideRecoveryAction(eq(risk), any(RecoveryPolicyResult.class), eq(failedAttempt), eq(provider)))
                .thenReturn(decision);
        when(recoveryActionRepository.findTopByPayment_PaymentIdAndActionTypeAndStatusInOrderByAttemptNumberDesc(
                eq(payment.getPaymentId()), eq(RecoveryActionType.RETRY), any()))
                .thenReturn(Optional.empty());
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryExecutor.execute(any(UUID.class))).thenReturn(new RecoveryResult(
                UUID.randomUUID(), null, RecoveryResultOutcome.RECOVERED, payment.getAmount(), payment.getCurrency(), payment, OffsetDateTime.now()));

        RecoveryAction action = recoveryService.processFailedPayment(payment.getPaymentId());

        assertNotNull(action);
        assertEquals(RecoveryActionType.RETRY, action.getActionType());
        assertEquals(decision.getDecisionId(), action.getDecisionId());
        verify(recoveryDecisionService).decideRecoveryAction(eq(risk), any(RecoveryPolicyResult.class), eq(failedAttempt), eq(provider));
        verify(recoveryActionRepository).save(any(RecoveryAction.class));
        verify(recoveryExecutor).execute(action.getActionId());
    }

    @Test
    void shouldBlockUnknownPaymentFromRecovery() {
        Payment payment = createPayment(PaymentStatus.UNKNOWN);

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));

        RecoveryAction action = recoveryService.processFailedPayment(payment.getPaymentId());

        assertNull(action);
        verify(recoveryActionRepository, never()).save(any(RecoveryAction.class));
    }

    @Test
    void shouldNotCreateDuplicateRecoveryAction() {
        Payment payment = createPayment(PaymentStatus.FAILED);
        Provider provider = createProvider("Primary");
        PaymentAttempt failedAttempt = createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1);
        RecoveryAction existingAction = new RecoveryAction(
                UUID.randomUUID(), UUID.randomUUID(), payment, UUID.randomUUID(), RecoveryActionType.RETRY,
                provider, RecoveryActionStatus.PENDING, 2, null, null, "retryable failure");
        com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk risk = new com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk(
                existingAction.getRiskId(), payment.getMerchant(), payment,
                com.system_project.distributedpaymentsystem.domain.entity.RiskType.PAYMENT_FAILURE,
                payment.getAmount(), payment.getCurrency(), BigDecimal.valueOf(1.0),
                "test", com.system_project.distributedpaymentsystem.domain.entity.RevenueRiskStatus.IN_RECOVERY,
                OffsetDateTime.now(), null
        );
        com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision decision = new com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision(
                UUID.randomUUID(), risk, com.system_project.distributedpaymentsystem.domain.entity.DecisionType.RETRY,
                "Retry is eligible", BigDecimal.valueOf(0.95), "gemini-2.0-flash", true, OffsetDateTime.now()
        );

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(revenueRiskService.createIfAbsent(payment)).thenReturn(Optional.of(risk));
        when(revenueRiskService.claimForDecision(risk.getRiskId())).thenReturn(ClaimResult.success());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(failedAttempt));
        when(recoveryPolicy.evaluate(eq(payment), eq(provider), any(ProviderProcessingResult.class)))
                .thenReturn(new RecoveryPolicyResult(RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                        RecoveryActionType.RETRY, true, false, false));
        when(recoveryDecisionService.decideRecoveryAction(eq(risk), any(RecoveryPolicyResult.class), eq(failedAttempt), eq(provider)))
                .thenReturn(decision);
        when(recoveryActionRepository.findTopByPayment_PaymentIdAndActionTypeAndStatusInOrderByAttemptNumberDesc(
                eq(payment.getPaymentId()), eq(RecoveryActionType.RETRY), any()))
                .thenReturn(Optional.of(existingAction));

        RecoveryAction action = recoveryService.processFailedPayment(payment.getPaymentId());

        assertEquals(existingAction.getActionId(), action.getActionId());
        verify(recoveryActionRepository, never()).save(any(RecoveryAction.class));
        verify(recoveryExecutor, never()).execute(any(UUID.class));
    }

    private Payment createPayment(PaymentStatus status) {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
        return new Payment(
                UUID.randomUUID(),
                merchant,
                new BigDecimal("100.00"),
                "USD",
                status,
                "idempotency-key",
                "customer-ref",
                "order-ref",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                PaymentType.PAYMENT,
                "description"
        );
    }

    private Provider createProvider(String name) {
        return new Provider(
                UUID.randomUUID(),
                name,
                name.toLowerCase().replace(" ", "-"),
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PaymentAttempt createAttempt(Payment payment, Provider provider, PaymentAttemptStatus status, int number) {
        return new PaymentAttempt(
                UUID.randomUUID(),
                payment,
                provider,
                number,
                payment.getAmount(),
                payment.getCurrency(),
                status,
                "REF-" + number,
                status == PaymentAttemptStatus.TIMEOUT ? "TIMEOUT" : "ERROR",
                "mock failure",
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
