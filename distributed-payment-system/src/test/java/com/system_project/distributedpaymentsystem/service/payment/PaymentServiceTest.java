package com.system_project.distributedpaymentsystem.service.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentRequest;
import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentResponse;
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
import com.system_project.distributedpaymentsystem.provider.PaymentProvider;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.exception.DuplicateIdempotencyKeyException;
import com.system_project.distributedpaymentsystem.repository.MerchantRepository;
import com.system_project.distributedpaymentsystem.repository.OutboxEventRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.service.idempotency.IdempotencyService;
import com.system_project.distributedpaymentsystem.service.idempotency.IdempotencyReservation;
import com.system_project.distributedpaymentsystem.service.provider.ProviderRouter;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private ProviderRouter providerRouter;

    @Mock
    private IdempotencyService idempotencyService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                merchantRepository,
                paymentRepository,
                paymentAttemptRepository,
                outboxEventRepository,
                paymentProvider,
                providerRouter,
                idempotencyService
        );
    }

    @Test
    void createPayment_shouldTransitionToProcessingBeforeProviderInvocation() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("125.50"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-1001");
        request.setOrderRef("ORDER-2001");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("Test payment");

        Provider provider = new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerRouter.selectProvider(merchantId)).thenReturn(provider);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyService.reserve(eq(merchantId), eq("idem-123"), anyString())).thenReturn(new IdempotencyReservation(true, false, null));
        when(paymentProvider.processPayment(any(Payment.class), any(Provider.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
                    return new ProviderProcessingResult(true, "MOCK-REF-001", null, null);
                });

        CreatePaymentResponse response = paymentService.createPayment(merchantId, request, "idem-123");

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
    }

    @Test
    void createPayment_shouldPersistPaymentSuccessOutboxEvent() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("125.50"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-OUTBOX-SUCCESS");
        request.setOrderRef("ORDER-OUTBOX-SUCCESS");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("Outbox success test");

        Provider provider = new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerRouter.selectProvider(merchantId)).thenReturn(provider);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyService.reserve(eq(merchantId), eq("outbox-success"), anyString())).thenReturn(new IdempotencyReservation(true, false, null));
        when(paymentProvider.processPayment(any(Payment.class), any(Provider.class)))
                .thenReturn(new ProviderProcessingResult(true, "MOCK-REF-OUTBOX", null, null));

        CreatePaymentResponse response = paymentService.createPayment(merchantId, request, "outbox-success");

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertNotNull(outboxCaptor.getValue());
    }

    @Test
    void createPayment_shouldPersistFailureOutboxEvent() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("75.00"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-OUTBOX-FAIL");
        request.setOrderRef("ORDER-OUTBOX-FAIL");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("Outbox failure test");

        Provider provider = new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerRouter.selectProvider(merchantId)).thenReturn(provider);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyService.reserve(eq(merchantId), eq("outbox-fail"), anyString())).thenReturn(new IdempotencyReservation(true, false, null));
        when(paymentProvider.processPayment(any(Payment.class), any(Provider.class)))
                .thenReturn(new ProviderProcessingResult(false, "FAIL-REF", "DECLINED", "card declined"));

        CreatePaymentResponse response = paymentService.createPayment(merchantId, request, "outbox-fail");

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        verify(outboxEventRepository, atLeastOnce()).save(any());
    }

    @Test
    void createPayment_shouldPersistUnknownOutboxEvent() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("90.00"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-OUTBOX-UNKNOWN");
        request.setOrderRef("ORDER-OUTBOX-UNKNOWN");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("Outbox unknown test");

        Provider provider = new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerRouter.selectProvider(merchantId)).thenReturn(provider);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyService.reserve(eq(merchantId), eq("outbox-unknown"), anyString())).thenReturn(new IdempotencyReservation(true, false, null));
        when(paymentProvider.processPayment(any(Payment.class), any(Provider.class)))
                .thenReturn(ProviderProcessingResult.timeout("TIMEOUT-REF", "TIMEOUT", "provider timeout"));

        CreatePaymentResponse response = paymentService.createPayment(merchantId, request, "outbox-unknown");

        assertNotNull(response);
        assertEquals(PaymentStatus.UNKNOWN, response.getStatus());
        verify(outboxEventRepository, atLeastOnce()).save(any());
    }

    @Test
    void createPayment_shouldPersistPaymentAndReturnResponse() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("125.50"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-1001");
        request.setOrderRef("ORDER-2001");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("Test payment");

        Provider provider = new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerRouter.selectProvider(merchantId)).thenReturn(provider);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyService.reserve(eq(merchantId), eq("idem-123"), anyString())).thenReturn(new IdempotencyReservation(true, false, null));
        when(paymentProvider.processPayment(any(Payment.class), any(Provider.class)))
                .thenReturn(new ProviderProcessingResult(true, "MOCK-REF-001", null, null));

        CreatePaymentResponse response = paymentService.createPayment(merchantId, request, "idem-123");

        assertNotNull(response);
        assertEquals(merchantId, response.getMerchantId());
        assertEquals(request.getAmount(), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals(PaymentType.PAYMENT, response.getPaymentType());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals("CUST-1001", response.getCustomerRef());
        assertEquals("ORDER-2001", response.getOrderRef());
        assertEquals("Test payment", response.getDescription());

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentRepository, atLeastOnce()).save(any(Payment.class));
        verify(paymentAttemptRepository, atLeastOnce()).save(attemptCaptor.capture());

        PaymentAttempt savedAttempt = attemptCaptor.getValue();
        assertNotNull(savedAttempt);
        assertEquals(PaymentAttemptStatus.SUCCESS, savedAttempt.getStatus());
        assertEquals(Integer.valueOf(1), savedAttempt.getAttemptNumber());
        assertEquals(response.getPaymentId(), savedAttempt.getPayment().getPaymentId());
    }

    @Test
    void createPayment_shouldMarkPaymentAsUnknownWhenProviderTimesOut() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("125.50"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-TIMEOUT");
        request.setOrderRef("ORDER-TIMEOUT");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("TIMEOUT TEST");

        Provider provider = new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerRouter.selectProvider(merchantId)).thenReturn(provider);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyService.reserve(eq(merchantId), eq("timeout-key"), anyString())).thenReturn(new IdempotencyReservation(true, false, null));
        when(paymentProvider.processPayment(any(Payment.class), any(Provider.class)))
                .thenReturn(ProviderProcessingResult.timeout("TIMEOUT-REF", "TIMEOUT", "provider timeout"));

        CreatePaymentResponse response = paymentService.createPayment(merchantId, request, "timeout-key");

        assertNotNull(response);
        assertEquals(PaymentStatus.UNKNOWN, response.getStatus());

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository, atLeastOnce()).save(attemptCaptor.capture());
        assertEquals(PaymentAttemptStatus.TIMEOUT, attemptCaptor.getValue().getStatus());
    }

    @Test
    void createPayment_shouldRejectDifferentRequestForSameIdempotencyKey() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("125.50"));
        request.setCurrency("USD");
        request.setCustomerRef("CUST-1001");
        request.setOrderRef("ORDER-2001");
        request.setPaymentType(PaymentType.PAYMENT);
        request.setDescription("Test payment");

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(idempotencyService.reserve(eq(merchantId), eq("idem-123"), anyString()))
                .thenReturn(new IdempotencyReservation(false, false, "different-fingerprint"));

        DuplicateIdempotencyKeyException exception = org.junit.jupiter.api.Assertions.assertThrows(
                DuplicateIdempotencyKeyException.class,
                () -> paymentService.createPayment(merchantId, request, "idem-123")
        );

        assertNotNull(exception);
    }
}
