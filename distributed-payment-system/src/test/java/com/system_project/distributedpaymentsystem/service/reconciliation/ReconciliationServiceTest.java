package com.system_project.distributedpaymentsystem.service.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEvent;
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
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationResolvedStatus;
import com.system_project.distributedpaymentsystem.provider.PaymentProvider;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.OutboxEventRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private ReconciliationCaseRepository reconciliationCaseRepository;

    @Mock
    private ReconciliationAttemptRepository reconciliationAttemptRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentProvider paymentProvider;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(
                paymentRepository,
                paymentAttemptRepository,
                reconciliationCaseRepository,
                reconciliationAttemptRepository,
                outboxEventRepository,
                paymentProvider
        );
    }

    @Test
    void reconcilePayment_shouldResolveUnknownPaymentToSuccess_whenProviderConfirmsSuccess() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null);
        Payment payment = new Payment(UUID.randomUUID(), merchant, new BigDecimal("100.00"), "USD",
                PaymentStatus.UNKNOWN, "idem-reconcile-success", "CUST-RECONCILE-SUCCESS",
                "ORDER-RECONCILE-SUCCESS", OffsetDateTime.now(), OffsetDateTime.now(),
                PaymentType.PAYMENT, "timeout reconcile success");
        Provider provider = new Provider(UUID.randomUUID(), "Primary Provider", "primary-provider", 1,
                ProviderStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1,
                payment.getAmount(), payment.getCurrency(), PaymentAttemptStatus.TIMEOUT,
                "MOCK-TIMEOUT-SUCCESS", "TIMEOUT", "provider timeout while waiting for final outcome",
                null, null, null, OffsetDateTime.now(), OffsetDateTime.now());

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(attempt));
        when(paymentProvider.getPaymentStatus(payment, provider, attempt.getProviderReference()))
                .thenReturn(ProviderProcessingResult.success(attempt.getProviderReference(), null, "provider confirmed success"));
        when(reconciliationCaseRepository.findByPayment_PaymentIdAndAttempt_AttemptId(payment.getPaymentId(), attempt.getAttemptId()))
                .thenReturn(Optional.empty());
        when(reconciliationCaseRepository.save(any(ReconciliationCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationAttemptRepository.save(any(com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationCase result = reconciliationService.reconcilePayment(payment.getPaymentId());

        assertNotNull(result);
        assertEquals(ReconciliationCaseStatus.RESOLVED, result.getStatus());
        assertEquals(ReconciliationReason.PROVIDER_TIMEOUT, result.getReason());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(PaymentAttemptStatus.SUCCESS, attempt.getStatus());

        // Verify PAYMENT_SUCCEEDED event is created
        verify(outboxEventRepository).save(argThat(event ->
            event.getEventType().equals("PAYMENT_SUCCEEDED") &&
            event.getAggregateId().equals(payment.getPaymentId())
        ));
    }

    @Test
    void reconcilePayment_shouldResolveUnknownPaymentToFailed_whenProviderConfirmsFailure() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null);
        Payment payment = new Payment(UUID.randomUUID(), merchant, new BigDecimal("100.00"), "USD",
                PaymentStatus.UNKNOWN, "idem-reconcile-failed", "CUST-RECONCILE-FAILED",
                "ORDER-RECONCILE-FAILED", OffsetDateTime.now(), OffsetDateTime.now(),
                PaymentType.PAYMENT, "timeout reconcile failed");
        Provider provider = new Provider(UUID.randomUUID(), "Primary Provider", "primary-provider", 1,
                ProviderStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1,
                payment.getAmount(), payment.getCurrency(), PaymentAttemptStatus.TIMEOUT,
                "MOCK-TIMEOUT-FAILED", "TIMEOUT", "provider timeout while waiting for final outcome",
                null, null, null, OffsetDateTime.now(), OffsetDateTime.now());

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(attempt));
        when(paymentProvider.getPaymentStatus(payment, provider, attempt.getProviderReference()))
                .thenReturn(ProviderProcessingResult.failed(attempt.getProviderReference(), "FAILED", "provider confirmed failure"));
        when(reconciliationCaseRepository.findByPayment_PaymentIdAndAttempt_AttemptId(payment.getPaymentId(), attempt.getAttemptId()))
                .thenReturn(Optional.empty());
        when(reconciliationCaseRepository.save(any(ReconciliationCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationAttemptRepository.save(any(com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationCase result = reconciliationService.reconcilePayment(payment.getPaymentId());

        assertNotNull(result);
        assertEquals(ReconciliationCaseStatus.RESOLVED, result.getStatus());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(PaymentAttemptStatus.FAILED, attempt.getStatus());

        // Verify PAYMENT_FAILED event is created
        verify(outboxEventRepository).save(argThat(event ->
            event.getEventType().equals("PAYMENT_FAILED") &&
            event.getAggregateId().equals(payment.getPaymentId())
        ));
    }

    @Test
    void reconcilePayment_shouldNotEmitEventWhenStatusStillUnknown() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null);
        Payment payment = new Payment(UUID.randomUUID(), merchant, new BigDecimal("100.00"), "USD",
                PaymentStatus.UNKNOWN, "idem-reconcile-unknown", "CUST-RECONCILE-UNKNOWN",
                "ORDER-RECONCILE-UNKNOWN", OffsetDateTime.now(), OffsetDateTime.now(),
                PaymentType.PAYMENT, "timeout reconcile unknown");
        Provider provider = new Provider(UUID.randomUUID(), "Primary Provider", "primary-provider", 1,
                ProviderStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1,
                payment.getAmount(), payment.getCurrency(), PaymentAttemptStatus.TIMEOUT,
                "MOCK-TIMEOUT-UNKNOWN", "TIMEOUT", "provider timeout while waiting for final outcome",
                null, null, null, OffsetDateTime.now(), OffsetDateTime.now());

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(attempt));
        // Provider still doesn't know
        when(paymentProvider.getPaymentStatus(payment, provider, attempt.getProviderReference()))
                .thenReturn(ProviderProcessingResult.timeout(attempt.getProviderReference(), "UNKNOWN", "provider still cannot confirm status"));
        when(reconciliationCaseRepository.findByPayment_PaymentIdAndAttempt_AttemptId(payment.getPaymentId(), attempt.getAttemptId()))
                .thenReturn(Optional.empty());
        when(reconciliationCaseRepository.save(any(ReconciliationCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationAttemptRepository.save(any(com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationCase result = reconciliationService.reconcilePayment(payment.getPaymentId());

        assertNotNull(result);
        assertEquals(ReconciliationCaseStatus.FAILED, result.getStatus());
        assertEquals(PaymentStatus.UNKNOWN, payment.getStatus());
        assertEquals(PaymentAttemptStatus.TIMEOUT, attempt.getStatus());

        // Verify NO terminal event is created
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void reconcilePayment_shouldRejectAlreadyResolvedPayment() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null);
        Payment payment = new Payment(UUID.randomUUID(), merchant, new BigDecimal("100.00"), "USD",
                PaymentStatus.SUCCESS, "idem-success", "CUST-SUCCESS", "ORDER-SUCCESS",
                OffsetDateTime.now(), OffsetDateTime.now(), PaymentType.PAYMENT, "already success");

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));

        assertThrows(IllegalStateException.class, () -> reconciliationService.reconcilePayment(payment.getPaymentId()));
    }

    @Test
    void reconcilePayment_shouldReuseExistingCaseForDuplicateUnknownTrigger() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Merchant", "merchant@test.com", null,
                MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null);
        Payment payment = new Payment(UUID.randomUUID(), merchant, new BigDecimal("100.00"), "USD",
                PaymentStatus.UNKNOWN, "idem-reconcile-duplicate", "CUST-RECONCILE-DUPLICATE",
                "ORDER-RECONCILE-DUPLICATE", OffsetDateTime.now(), OffsetDateTime.now(),
                PaymentType.PAYMENT, "timeout reconcile duplicate");
        Provider provider = new Provider(UUID.randomUUID(), "Primary Provider", "primary-provider", 1,
                ProviderStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1,
                payment.getAmount(), payment.getCurrency(), PaymentAttemptStatus.TIMEOUT,
                "MOCK-TIMEOUT-DUPLICATE", "TIMEOUT", "provider timeout while waiting for final outcome",
                null, null, null, OffsetDateTime.now(), OffsetDateTime.now());
        ReconciliationCase existingCase = new ReconciliationCase(UUID.randomUUID(), payment, attempt,
                ReconciliationCaseStatus.OPEN, ReconciliationReason.PROVIDER_TIMEOUT, "TIMEOUT",
                null, 0, OffsetDateTime.now(), OffsetDateTime.now(), null);

        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId()))
                .thenReturn(List.of(attempt));
        when(paymentProvider.getPaymentStatus(payment, provider, attempt.getProviderReference()))
                .thenReturn(ProviderProcessingResult.success(attempt.getProviderReference(), null, "provider confirmed success"));
        when(reconciliationCaseRepository.findByPayment_PaymentIdAndAttempt_AttemptId(payment.getPaymentId(), attempt.getAttemptId()))
                .thenReturn(Optional.of(existingCase));
        when(reconciliationCaseRepository.save(any(ReconciliationCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationAttemptRepository.save(any(com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationCase result = reconciliationService.reconcilePayment(payment.getPaymentId());

        assertEquals(existingCase.getReconciliationId(), result.getReconciliationId());
        assertEquals(ReconciliationCaseStatus.RESOLVED, result.getStatus());
        assertEquals(ReconciliationResolvedStatus.SUCCESS, result.getResolvedStatus());
        verify(reconciliationCaseRepository).save(existingCase);

        // Verify PAYMENT_SUCCEEDED event is created
        verify(outboxEventRepository).save(argThat(event ->
            event.getEventType().equals("PAYMENT_SUCCEEDED") &&
            event.getAggregateId().equals(payment.getPaymentId())
        ));
    }
}
