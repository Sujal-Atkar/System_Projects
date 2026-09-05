package com.system_project.distributedpaymentsystem.service.payment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentRequest;
import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentResponse;
import com.system_project.distributedpaymentsystem.api.payment.dto.GetPaymentResponse;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEvent;
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEventStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttemptStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
// import com.system_project.distributedpaymentsystem.domain.entity.IdempotencyRecord;
// import com.system_project.distributedpaymentsystem.domain.entity.IdempotencyRecordStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.exception.DuplicateIdempotencyKeyException;
import com.system_project.distributedpaymentsystem.exception.MerchantNotFoundException;
import com.system_project.distributedpaymentsystem.exception.PaymentNotFoundException;
import com.system_project.distributedpaymentsystem.provider.PaymentProvider;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
// import com.system_project.distributedpaymentsystem.repository.IdempotencyRecordRepository;
import com.system_project.distributedpaymentsystem.repository.MerchantRepository;
import com.system_project.distributedpaymentsystem.repository.OutboxEventRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.service.idempotency.IdempotencyReservation;
import com.system_project.distributedpaymentsystem.service.idempotency.IdempotencyService;
import com.system_project.distributedpaymentsystem.service.provider.ProviderRouter;

@Service
public class PaymentService {

    private final MerchantRepository merchantRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentProvider paymentProvider;
    private final ProviderRouter providerRouter;
    private final IdempotencyService idempotencyService;

    public PaymentService(MerchantRepository merchantRepository,
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentProvider paymentProvider,
            ProviderRouter providerRouter,
            IdempotencyService idempotencyService) {
        this.merchantRepository = merchantRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentProvider = paymentProvider;
        this.providerRouter = providerRouter;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public CreatePaymentResponse createPayment(UUID merchantId, CreatePaymentRequest request) {
        return createPayment(merchantId, request, UUID.randomUUID().toString());
    }

    @Transactional
    public CreatePaymentResponse createPayment(UUID merchantId, CreatePaymentRequest request, String idempotencyKey) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new IllegalStateException("Merchant is not active: " + merchantId);
        }

        String normalizedKey = idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey.trim();
        String requestFingerprint = buildRequestFingerprint(request);
        IdempotencyReservation reservation = idempotencyService.reserve(merchantId, normalizedKey, requestFingerprint);

        if (!reservation.reserved()) {
            if (reservation.duplicate()) {
                Payment existingPayment = paymentRepository.findByMerchant_MerchantIdAndIdempotencyKey(merchantId, normalizedKey)
                        .orElse(null);
                if (existingPayment != null) {
                    return toCreatePaymentResponse(existingPayment);
                }
            }
            throw new DuplicateIdempotencyKeyException(merchantId, normalizedKey);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String currency = request.getCurrency() == null ? null : request.getCurrency().trim().toUpperCase(Locale.ROOT);

        Payment payment = new Payment(
                UUID.randomUUID(),
                merchant,
                request.getAmount(),
                currency,
                PaymentStatus.CREATED,
                normalizedKey,
                request.getCustomerRef(),
                request.getOrderRef(),
                now,
                now,
                request.getPaymentType(),
                request.getDescription()
        );

        Payment savedPayment = paymentRepository.save(payment);
        savedPayment.setStatus(PaymentStatus.PROCESSING);
        savedPayment.setUpdatedAt(now);
        paymentRepository.save(savedPayment);

        idempotencyService.attachPayment(merchantId, normalizedKey, savedPayment, requestFingerprint);

        Provider provider = providerRouter.selectProvider(merchantId);

        PaymentAttempt paymentAttempt = new PaymentAttempt(
                UUID.randomUUID(),
                savedPayment,
                provider,
                1,
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                PaymentAttemptStatus.PROCESSING,
                null,
                null,
                null,
                Map.of("providerCode", provider.getProviderCode(), "merchantId", merchantId),
                null,
                null,
                now,
                null
        );

        paymentAttemptRepository.save(paymentAttempt);

        ProviderProcessingResult providerResult = paymentProvider.processPayment(savedPayment, provider);

        if (providerResult != null && providerResult.isSuccess()) {
            paymentProvider.recordSuccess(provider);
        } else if (providerResult != null && (providerResult.isTimeout()
                || "TIMEOUT".equalsIgnoreCase(providerResult.getErrorCode())
                || "UNKNOWN".equalsIgnoreCase(providerResult.getErrorCode()))) {
            paymentProvider.recordFailure(provider);
        }

        PaymentAttemptStatus attemptStatus = providerResult.isSuccess()
                ? PaymentAttemptStatus.SUCCESS
                : providerResult.isTimeout() ? PaymentAttemptStatus.TIMEOUT : PaymentAttemptStatus.FAILED;

        paymentAttempt.setStatus(attemptStatus);
        paymentAttempt.setProviderReference(providerResult.getProviderReference());
        paymentAttempt.setErrorCode(providerResult.getErrorCode());
        paymentAttempt.setFailureReason(providerResult.getFailureReason());

        Map<String, Object> responsePayload = new java.util.HashMap<>();
        responsePayload.put("providerReference", providerResult.getProviderReference());
        responsePayload.put("success", providerResult.isSuccess());
        responsePayload.put("outcome", providerResult.isSuccess() ? "SUCCESS" : providerResult.isTimeout() ? "TIMEOUT" : "FAILED");
        if (providerResult.getErrorCode() != null) {
            responsePayload.put("errorCode", providerResult.getErrorCode());
        }
        if (providerResult.getFailureReason() != null) {
            responsePayload.put("failureReason", providerResult.getFailureReason());
        }
        paymentAttempt.setResponsePayload(responsePayload);
        paymentAttempt.setRespondedAt(OffsetDateTime.now(ZoneOffset.UTC));

        PaymentStatus paymentStatus = providerResult.isSuccess()
                ? PaymentStatus.SUCCESS
                : providerResult.isTimeout() ? PaymentStatus.UNKNOWN : PaymentStatus.FAILED;

        savedPayment.setStatus(paymentStatus);
        savedPayment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        paymentRepository.save(savedPayment);
        paymentAttemptRepository.save(paymentAttempt);
        persistPaymentLifecycleOutboxEvent(savedPayment, paymentStatus);

        idempotencyService.complete(merchantId, normalizedKey, savedPayment, requestFingerprint,
                providerResult.isSuccess() ? 200 : providerResult.isTimeout() ? 202 : 500, responsePayload);

        return toCreatePaymentResponse(savedPayment);
    }

    private void persistPaymentLifecycleOutboxEvent(Payment payment, PaymentStatus paymentStatus) {
        String eventType = switch (paymentStatus) {
            case SUCCESS -> "PAYMENT_SUCCEEDED";
            case FAILED -> "PAYMENT_FAILED";
            case UNKNOWN -> "PAYMENT_UNKNOWN";
            default -> null;
        };

        if (eventType == null) {
            return;
        }

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("paymentId", payment.getPaymentId().toString());
        payload.put("merchantId", payment.getMerchant().getMerchantId().toString());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("status", paymentStatus.name());
        payload.put("paymentType", payment.getPaymentType() == null ? null : payment.getPaymentType().name());
        payload.put("customerRef", payment.getCustomerRef());
        payload.put("orderRef", payment.getOrderRef());
        payload.put("idempotencyKey", payment.getIdempotencyKey());
        payload.put("createdAt", payment.getCreatedAt());
        payload.put("updatedAt", payment.getUpdatedAt());

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT",
                payment.getPaymentId(),
                eventType,
                payment.getPaymentId().toString(),
                payload,
                OutboxEventStatus.PENDING,
                0,
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        outboxEventRepository.save(outboxEvent);
    }

    private CreatePaymentResponse toCreatePaymentResponse(Payment payment) {
        return new CreatePaymentResponse(
                payment.getPaymentId(),
                payment.getMerchant().getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentType(),
                payment.getCustomerRef(),
                payment.getOrderRef(),
                payment.getIdempotencyKey(),
                payment.getDescription(),
                payment.getCreatedAt()
        );
    }

    private String buildRequestFingerprint(CreatePaymentRequest request) {
        return java.util.Objects.requireNonNullElse(request.getCustomerRef(), "") + "|"
                + java.util.Objects.requireNonNullElse(request.getOrderRef(), "") + "|"
                + java.util.Objects.requireNonNullElse(request.getAmount(), java.math.BigDecimal.ZERO) + "|"
                + java.util.Objects.requireNonNullElse(request.getCurrency(), "") + "|"
                + java.util.Objects.requireNonNullElse(request.getPaymentType(), "") + "|"
                + java.util.Objects.requireNonNullElse(request.getDescription(), "");
    }

    @Transactional
    public PaymentAttempt executeRecoveryAttempt(UUID merchantId, Payment payment, Provider provider,
            String syntheticIdempotencyKey, RecoveryAction recoveryAction) {
        if (payment == null || provider == null) {
            throw new IllegalArgumentException("Payment and provider are required for recovery execution");
        }

        String requestFingerprint = "RECOVERY:" + (recoveryAction != null ? recoveryAction.getActionId() : UUID.randomUUID())
                + "|" + payment.getPaymentId() + "|" + provider.getProviderId();

        IdempotencyReservation reservation = idempotencyService.reserve(merchantId, syntheticIdempotencyKey, requestFingerprint);
        if (!reservation.reserved()) {
            return null;
        }

        int nextAttemptNumber = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId())
                .stream()
                .mapToInt(PaymentAttempt::getAttemptNumber)
                .max()
                .orElse(0) + 1;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentAttempt paymentAttempt = new PaymentAttempt(
                UUID.randomUUID(),
                payment,
                provider,
                nextAttemptNumber,
                payment.getAmount(),
                payment.getCurrency(),
                PaymentAttemptStatus.PROCESSING,
                null,
                null,
                null,
                Map.of(
                        "providerCode", provider.getProviderCode(),
                        "merchantId", merchantId,
                        "recoveryActionId", recoveryAction != null ? String.valueOf(recoveryAction.getActionId()) : "NONE",
                        "idempotencyKey", syntheticIdempotencyKey,
                        "paymentId", payment.getPaymentId()
                ),
                null,
                null,
                now,
                null
        );

        paymentAttemptRepository.save(paymentAttempt);

        ProviderProcessingResult providerResult = paymentProvider.processPayment(payment, provider);

        if (providerResult != null && providerResult.isSuccess()) {
            paymentProvider.recordSuccess(provider);
        } else if (providerResult != null && (providerResult.isTimeout()
                || "TIMEOUT".equalsIgnoreCase(providerResult.getErrorCode())
                || "UNKNOWN".equalsIgnoreCase(providerResult.getErrorCode()))) {
            paymentProvider.recordFailure(provider);
        }

        PaymentAttemptStatus attemptStatus = providerResult == null || providerResult.isSuccess()
                ? PaymentAttemptStatus.SUCCESS
                : providerResult.isTimeout() ? PaymentAttemptStatus.TIMEOUT : PaymentAttemptStatus.FAILED;

        paymentAttempt.setStatus(attemptStatus);
        paymentAttempt.setProviderReference(providerResult != null ? providerResult.getProviderReference() : null);
        paymentAttempt.setErrorCode(providerResult != null ? providerResult.getErrorCode() : null);
        paymentAttempt.setFailureReason(providerResult != null ? providerResult.getFailureReason() : null);

        Map<String, Object> responsePayload = new java.util.HashMap<>();
        if (providerResult != null) {
            responsePayload.put("providerReference", providerResult.getProviderReference());
            responsePayload.put("success", providerResult.isSuccess());
            responsePayload.put("outcome", providerResult.isSuccess() ? "SUCCESS" : providerResult.isTimeout() ? "TIMEOUT" : "FAILED");
            if (providerResult.getErrorCode() != null) {
                responsePayload.put("errorCode", providerResult.getErrorCode());
            }
            if (providerResult.getFailureReason() != null) {
                responsePayload.put("failureReason", providerResult.getFailureReason());
            }
        }
        paymentAttempt.setResponsePayload(responsePayload);
        paymentAttempt.setRespondedAt(OffsetDateTime.now(ZoneOffset.UTC));

        PaymentStatus paymentStatus = providerResult == null || providerResult.isSuccess()
                ? PaymentStatus.SUCCESS
                : providerResult.isTimeout() ? PaymentStatus.UNKNOWN : PaymentStatus.FAILED;

        payment.setStatus(paymentStatus);
        payment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        paymentRepository.save(payment);
        paymentAttemptRepository.save(paymentAttempt);
        persistPaymentLifecycleOutboxEvent(payment, paymentStatus);

        idempotencyService.complete(merchantId, syntheticIdempotencyKey, payment, requestFingerprint,
                providerResult == null || providerResult.isSuccess() ? 200 : providerResult.isTimeout() ? 202 : 500,
                responsePayload);

        return paymentAttempt;
    }

    public GetPaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        return new GetPaymentResponse(
                payment.getPaymentId(),
                payment.getMerchant().getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentType(),
                payment.getCustomerRef(),
                payment.getOrderRef(),
                payment.getIdempotencyKey(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
