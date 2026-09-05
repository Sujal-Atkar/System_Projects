package com.system_project.distributedpaymentsystem.service.reconciliation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.OutboxEvent;
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEventStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttemptStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttemptResult;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCase;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationReason;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationResolvedStatus;
import com.system_project.distributedpaymentsystem.exception.PaymentNotFoundException;
import com.system_project.distributedpaymentsystem.provider.PaymentProvider;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.OutboxEventRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;

@Service
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final ReconciliationAttemptRepository reconciliationAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentProvider paymentProvider;

    public ReconciliationService(PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            ReconciliationAttemptRepository reconciliationAttemptRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentProvider paymentProvider) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.reconciliationAttemptRepository = reconciliationAttemptRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public ReconciliationCase reconcilePayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.UNKNOWN) {
            throw new IllegalStateException("Payment is not UNKNOWN and cannot be reconciled: " + paymentId);
        }

        PaymentAttempt timeoutAttempt = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(paymentId)
                .stream()
                .filter(attempt -> attempt.getStatus() == PaymentAttemptStatus.TIMEOUT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No timeout attempt found for payment: " + paymentId));

        Provider provider = timeoutAttempt.getProvider();
        String providerReference = timeoutAttempt.getProviderReference();
        ProviderProcessingResult providerResult = paymentProvider.getPaymentStatus(payment, provider, providerReference);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReconciliationCase reconciliationCase = reconciliationCaseRepository
                .findByPayment_PaymentIdAndAttempt_AttemptId(paymentId, timeoutAttempt.getAttemptId())
                .orElseGet(() -> new ReconciliationCase(
                        UUID.randomUUID(),
                        payment,
                        timeoutAttempt,
                        ReconciliationCaseStatus.OPEN,
                        ReconciliationReason.PROVIDER_TIMEOUT,
                        providerResult.getOutcome().name(),
                        null,
                        0,
                        now,
                        now,
                        null));

        reconciliationCase.setStatus(ReconciliationCaseStatus.IN_PROGRESS);
        reconciliationCase.setReason(ReconciliationReason.PROVIDER_TIMEOUT);
        reconciliationCase.setProviderStatus(providerResult.getOutcome().name());
        reconciliationCase.setRetryCount(reconciliationCase.getRetryCount() == null ? 0 : reconciliationCase.getRetryCount() + 1);
        reconciliationCase.setNextCheckAt(null);
        reconciliationCase.setCreatedAt(reconciliationCase.getCreatedAt() == null ? now : reconciliationCase.getCreatedAt());

        ReconciliationAttempt reconciliationAttempt = new ReconciliationAttempt(
                UUID.randomUUID(),
                reconciliationCase,
                reconciliationCase.getRetryCount(),
                now,
                now,
                providerResult.getOutcome().name(),
                providerResult.isSuccess() ? ReconciliationAttemptResult.SUCCESS
                        : providerResult.isFailed() ? ReconciliationAttemptResult.FAILED
                        : ReconciliationAttemptResult.STILL_UNKNOWN,
                providerResult.getFailureReason());

        if (providerResult.isSuccess()) {
            timeoutAttempt.setStatus(PaymentAttemptStatus.SUCCESS);
            timeoutAttempt.setRespondedAt(now);
            timeoutAttempt.setFailureReason(null);
            timeoutAttempt.setErrorCode(null);

            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setUpdatedAt(now);

            reconciliationCase.setStatus(ReconciliationCaseStatus.RESOLVED);
            reconciliationCase.setResolvedStatus(ReconciliationResolvedStatus.SUCCESS);
            reconciliationCase.setResolvedAt(now);
            reconciliationCase.setProviderStatus("SUCCESS");
        } else if (providerResult.isFailed()) {
            timeoutAttempt.setStatus(PaymentAttemptStatus.FAILED);
            timeoutAttempt.setRespondedAt(now);
            timeoutAttempt.setFailureReason(providerResult.getFailureReason());
            timeoutAttempt.setErrorCode(providerResult.getErrorCode());

            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(now);

            reconciliationCase.setStatus(ReconciliationCaseStatus.RESOLVED);
            reconciliationCase.setResolvedStatus(ReconciliationResolvedStatus.FAILED);
            reconciliationCase.setResolvedAt(now);
            reconciliationCase.setProviderStatus("FAILED");
        } else {
            reconciliationCase.setStatus(ReconciliationCaseStatus.FAILED);
            reconciliationCase.setResolvedStatus(null);
            reconciliationCase.setResolvedAt(null);
        }

        paymentRepository.save(payment);
        paymentAttemptRepository.save(timeoutAttempt);
        reconciliationCaseRepository.save(reconciliationCase);
        reconciliationAttemptRepository.save(reconciliationAttempt);

        // Emit payment lifecycle event only when status transitions to terminal state
        if (providerResult.isSuccess()) {
            persistPaymentLifecycleOutboxEvent(payment, PaymentStatus.SUCCESS);
        } else if (providerResult.isFailed()) {
            persistPaymentLifecycleOutboxEvent(payment, PaymentStatus.FAILED);
        }
        // If STILL_UNKNOWN: no event emitted

        return reconciliationCase;
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
}
