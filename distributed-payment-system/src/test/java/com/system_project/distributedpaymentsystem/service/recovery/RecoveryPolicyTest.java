package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCase;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;
import com.system_project.distributedpaymentsystem.exception.NoEligibleProviderException;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.service.provider.ProviderCircuitState;
import com.system_project.distributedpaymentsystem.service.provider.ProviderHealthService;
import com.system_project.distributedpaymentsystem.service.provider.ProviderRouter;

@ExtendWith(MockitoExtension.class)
class RecoveryPolicyTest {

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private ReconciliationCaseRepository reconciliationCaseRepository;

    @Mock
    private ProviderHealthService providerHealthService;

    @Mock
    private ProviderRouter providerRouter;

    private RecoveryPolicy recoveryPolicy;

    @BeforeEach
    void setUp() {
        recoveryPolicy = new RecoveryPolicy(providerHealthService, providerRouter, paymentAttemptRepository, reconciliationCaseRepository, 3);
    }

    @Test
    void shouldBlockBusinessFailureWithNoRetry() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("DECLINED", "DECLINED", "customer declined");

        Mockito.when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertFalse(decision.isRetryAllowed());
        assertFalse(decision.isRerouteAllowed());
        assertEquals(RecoveryActionType.NO_ACTION, decision.getRecommendedAction());
        assertEquals(RecoveryPolicyReason.BUSINESS_FAILURE, decision.getReason());
    }

    @Test
    void shouldAllowRetryForTechnicalTransientFailure() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("UNAVAILABLE", "UNAVAILABLE", "temporary provider failure");

        Mockito.when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        Mockito.when(providerHealthService.getState(provider)).thenReturn(ProviderCircuitState.CLOSED);

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertTrue(decision.isRetryAllowed());
        assertEquals(RecoveryActionType.RETRY, decision.getRecommendedAction());
        assertEquals(RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE, decision.getReason());
    }

    @Test
    void shouldBlockRecoveryWhenMaxAttemptsReached() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("UNAVAILABLE", "UNAVAILABLE", "temporary provider failure");

        Mockito.when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(
                        createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1),
                        createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 2),
                        createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 3)
                ));

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertFalse(decision.isRetryAllowed());
        assertEquals(RecoveryPolicyReason.MAX_ATTEMPTS_REACHED, decision.getReason());
        assertEquals(RecoveryActionType.NO_ACTION, decision.getRecommendedAction());
    }

    @Test
    void shouldBlockRecoveryWhenPaymentIsUnknown() {
        Payment payment = createPayment(PaymentStatus.UNKNOWN);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("TIMEOUT", "TIMEOUT", "provider timeout");

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertFalse(decision.isRetryAllowed());
        assertFalse(decision.isRerouteAllowed());
        assertEquals(RecoveryPolicyReason.RECONCILIATION_OPEN, decision.getReason());
    }

    @Test
    void shouldBlockRecoveryWhenOpenReconciliationCaseExists() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("UNAVAILABLE", "UNAVAILABLE", "temporary provider failure");
        ReconciliationCase openCase = new ReconciliationCase(
                UUID.randomUUID(), payment, createAttempt(payment, provider, PaymentAttemptStatus.TIMEOUT, 1),
                ReconciliationCaseStatus.OPEN, com.system_project.distributedpaymentsystem.domain.entity.ReconciliationReason.PROVIDER_TIMEOUT,
                "TIMEOUT", null, 0, OffsetDateTime.now(), OffsetDateTime.now(), null);

        Mockito.when(reconciliationCaseRepository.findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(payment.getPaymentId(), ReconciliationCaseStatus.OPEN))
                .thenReturn(Optional.of(openCase));

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertFalse(decision.isRetryAllowed());
        assertEquals(RecoveryPolicyReason.RECONCILIATION_OPEN, decision.getReason());
    }

    @Test
    void shouldPreferRerouteWhenCircuitIsOpen() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        Provider alternate = createProvider("Provider B");
        ProviderProcessingResult result = ProviderProcessingResult.failed("UNAVAILABLE", "UNAVAILABLE", "temporary provider failure");

        Mockito.when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        Mockito.when(providerHealthService.getState(provider)).thenReturn(ProviderCircuitState.OPEN);
        Mockito.when(providerRouter.selectProvider(payment.getMerchant().getMerchantId())).thenReturn(alternate);

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertTrue(decision.isRerouteAllowed());
        assertEquals(RecoveryActionType.REROUTE, decision.getRecommendedAction());
    }

    @Test
    void shouldAllowRetryWhenCircuitIsClosed() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("CONNECTION_ERROR", "CONNECTION_ERROR", "temporary provider error");

        Mockito.when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        Mockito.when(providerHealthService.getState(provider)).thenReturn(ProviderCircuitState.CLOSED);

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertTrue(decision.isRetryAllowed());
        assertEquals(RecoveryActionType.RETRY, decision.getRecommendedAction());
    }

    @Test
    void shouldBlockWhenNoEligibleProviderExists() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        ProviderProcessingResult result = ProviderProcessingResult.failed("UNAVAILABLE", "UNAVAILABLE", "temporary provider failure");

        Mockito.when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        Mockito.when(providerHealthService.getState(provider)).thenReturn(ProviderCircuitState.OPEN);
        Mockito.when(providerRouter.selectProvider(payment.getMerchant().getMerchantId()))
                .thenThrow(new NoEligibleProviderException("No active provider available"));

        RecoveryPolicyResult decision = recoveryPolicy.evaluate(payment, provider, result);

        assertFalse(decision.isRetryAllowed());
        assertFalse(decision.isRerouteAllowed());
        assertEquals(RecoveryActionType.NO_ACTION, decision.getRecommendedAction());
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
                "idem-recovery",
                "CUST-100",
                "ORDER-100",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                PaymentType.PAYMENT,
                "recovery test"
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
