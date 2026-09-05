package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationReason;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryActionStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResult;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResultOutcome;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.repository.RecoveryActionRepository;
import com.system_project.distributedpaymentsystem.repository.RecoveryResultRepository;
import com.system_project.distributedpaymentsystem.service.payment.PaymentService;
import com.system_project.distributedpaymentsystem.service.provider.ProviderRouter;

@ExtendWith(MockitoExtension.class)
class RecoveryExecutorTest {

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @Mock
    private RecoveryResultRepository recoveryResultRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private ProviderRouter providerRouter;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private ReconciliationCaseRepository reconciliationCaseRepository;

    private RecoveryPolicy recoveryPolicy;
    private RecoveryExecutor recoveryExecutor;

    @BeforeEach
    void setUp() {
        recoveryPolicy = new RecoveryPolicy(null, providerRouter, paymentAttemptRepository, reconciliationCaseRepository, 3);
        recoveryExecutor = new RecoveryExecutor(
                recoveryActionRepository,
                recoveryResultRepository,
                paymentService,
                providerRouter,
                paymentAttemptRepository,
                reconciliationCaseRepository,
                recoveryPolicy,
                3
        );
    }

    @Test
    void shouldExecuteRetrySuccessUsingSamePaymentAndNewAttempt() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);
        PaymentAttempt attempt = createAttempt(payment, provider, PaymentAttemptStatus.SUCCESS, 2);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.empty());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        when(paymentService.executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(provider), eq("RECOVERY:" + action.getActionId()), eq(action)))
                .thenReturn(attempt);

        RecoveryResult result = recoveryExecutor.execute(action.getActionId());

        assertNotNull(result);
        assertEquals(RecoveryResultOutcome.RECOVERED, result.getOutcome());
        verify(paymentService).executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(provider), eq("RECOVERY:" + action.getActionId()), eq(action));
    }

    @Test
    void shouldExecuteRerouteSuccessAndUseAlternateProvider() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider original = createProvider("Provider A");
        Provider alternate = createProvider("Provider B");
        RecoveryAction action = createAction(payment, original, RecoveryActionType.REROUTE);
        PaymentAttempt attempt = createAttempt(payment, alternate, PaymentAttemptStatus.SUCCESS, 2);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.empty());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, original, PaymentAttemptStatus.FAILED, 1)));
        when(providerRouter.selectProvider(payment.getMerchant().getMerchantId())).thenReturn(alternate);
        when(paymentService.executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(alternate), eq("RECOVERY:" + action.getActionId()), eq(action)))
                .thenReturn(attempt);

        RecoveryResult result = recoveryExecutor.execute(action.getActionId());

        assertNotNull(result);
        assertEquals(RecoveryResultOutcome.RECOVERED, result.getOutcome());
        verify(paymentService).executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(alternate), eq("RECOVERY:" + action.getActionId()), eq(action));
    }

    @Test
    void shouldBlockExecutionWhenPaymentIsUnknown() {
        Payment payment = createPayment(PaymentStatus.UNKNOWN);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));

        assertThrows(IllegalStateException.class, () -> recoveryExecutor.execute(action.getActionId()));
        verify(paymentService, never()).executeRecoveryAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void shouldBlockExecutionWhenOpenReconciliationExists() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);
        ReconciliationCase openCase = new ReconciliationCase(
                UUID.randomUUID(),
                payment,
                createAttempt(payment, provider, PaymentAttemptStatus.TIMEOUT, 1),
                ReconciliationCaseStatus.OPEN,
                ReconciliationReason.PROVIDER_TIMEOUT,
                "TIMEOUT",
                null,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(reconciliationCaseRepository.findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(payment.getPaymentId(), ReconciliationCaseStatus.OPEN))
                .thenReturn(Optional.of(openCase));

        assertThrows(IllegalStateException.class, () -> recoveryExecutor.execute(action.getActionId()));
        verify(paymentService, never()).executeRecoveryAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void shouldBlockExecutionWhenAttemptCapReached() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.empty());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(
                        createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1),
                        createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 2),
                        createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 3)
                ));

        assertThrows(IllegalStateException.class, () -> recoveryExecutor.execute(action.getActionId()));
        verify(paymentService, never()).executeRecoveryAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPreventDuplicateRecoveryActionExecution() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);
        RecoveryResult existing = new RecoveryResult(UUID.randomUUID(), action, RecoveryResultOutcome.RECOVERED, payment.getAmount(), payment.getCurrency(), payment, OffsetDateTime.now());

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.of(existing));

        RecoveryResult result = recoveryExecutor.execute(action.getActionId());

        assertEquals(existing, result);
        verify(paymentService, never()).executeRecoveryAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void shouldTreatRecoveryTimeoutAsAmbiguousOutcome() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);
        PaymentAttempt attempt = createAttempt(payment, provider, PaymentAttemptStatus.TIMEOUT, 2);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.empty());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        when(paymentService.executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(provider), eq("RECOVERY:" + action.getActionId()), eq(action)))
                .thenReturn(attempt);

        RecoveryResult result = recoveryExecutor.execute(action.getActionId());

        assertEquals(RecoveryResultOutcome.PARTIALLY_RECOVERED, result.getOutcome());
    }

    @Test
    void shouldUseSyntheticRecoveryIdempotencyKey() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);
        PaymentAttempt attempt = createAttempt(payment, provider, PaymentAttemptStatus.SUCCESS, 2);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.empty());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        when(paymentService.executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(provider), eq("RECOVERY:" + action.getActionId()), eq(action)))
                .thenReturn(attempt);

        recoveryExecutor.execute(action.getActionId());

        verify(paymentService).executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(provider), eq("RECOVERY:" + action.getActionId()), eq(action));
    }

    @Test
    void shouldNotCreateNewPaymentDuringRecoveryExecution() {
        Payment payment = createPayment(PaymentStatus.PROCESSING);
        Provider provider = createProvider("Provider A");
        RecoveryAction action = createAction(payment, provider, RecoveryActionType.RETRY);
        PaymentAttempt attempt = createAttempt(payment, provider, PaymentAttemptStatus.SUCCESS, 2);

        when(recoveryActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));
        when(recoveryResultRepository.findByAction(action)).thenReturn(Optional.empty());
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(createAttempt(payment, provider, PaymentAttemptStatus.FAILED, 1)));
        when(paymentService.executeRecoveryAttempt(eq(payment.getMerchant().getMerchantId()), eq(payment), eq(provider), eq("RECOVERY:" + action.getActionId()), eq(action)))
                .thenReturn(attempt);

        RecoveryResult result = recoveryExecutor.execute(action.getActionId());

        assertNotNull(result);
        assertEquals(payment.getPaymentId(), result.getResultingPayment().getPaymentId());
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
                "merchant-original-key",
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

    private RecoveryAction createAction(Payment payment, Provider provider, RecoveryActionType actionType) {
        return new RecoveryAction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                payment,
                UUID.randomUUID(),
                actionType,
                provider,
                RecoveryActionStatus.PENDING,
                1,
                null,
                null,
                null
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
