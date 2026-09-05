package com.system_project.distributedpaymentsystem.service.recovery;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryContextResponse;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteRequest;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteResponse;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttemptStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.ProviderRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.service.idempotency.IdempotencyReservation;
import com.system_project.distributedpaymentsystem.service.idempotency.IdempotencyService;
import com.system_project.distributedpaymentsystem.service.payment.PaymentService;

@Service
public class InternalRecoveryExecutionService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentService paymentService;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final IdempotencyService idempotencyService;
    private final ProviderRepository providerRepository;

    public InternalRecoveryExecutionService(PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentService paymentService,
            ReconciliationCaseRepository reconciliationCaseRepository,
            IdempotencyService idempotencyService,
            ProviderRepository providerRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentService = paymentService;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.idempotencyService = idempotencyService;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public RecoveryContextResponse getContext(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        boolean isReconciliationOpen = reconciliationCaseRepository
                .findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(paymentId, ReconciliationCaseStatus.OPEN)
                .isPresent();

        List<PaymentAttempt> attempts = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(paymentId);
        int attemptsCount = attempts.size();

        RecoveryContextResponse response = new RecoveryContextResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setPaymentStatus(payment.getStatus().name());
        response.setAttemptNumber(attemptsCount);
        response.setPreviousAttempts(Math.max(0, attemptsCount - 1));

        if (!attempts.isEmpty()) {
            PaymentAttempt latestAttempt = attempts.get(attemptsCount - 1);
            response.setProvider(latestAttempt.getProvider().getProviderCode());
            response.setFailureType(latestAttempt.getErrorCode());
            response.setFailureReason(latestAttempt.getFailureReason());
            response.setLastAttemptAt(latestAttempt.getRequestedAt());
        }

        response.setProviderHealth("HEALTHY");
        response.setReconciliationOpen(isReconciliationOpen);

        return response;
    }

    @Transactional
    public RecoveryExecuteResponse executeRecovery(UUID paymentId, String idempotencyKey, RecoveryExecuteRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        boolean isReconciliationOpen = reconciliationCaseRepository
                .findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(paymentId, ReconciliationCaseStatus.OPEN)
                .isPresent();

        if (isReconciliationOpen) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RECONCILIATION_OPEN");
        }

        String fingerprint = request.getAction() + ":" + request.getTargetProvider();
        IdempotencyReservation reservation = idempotencyService.reserve(payment.getMerchant().getMerchantId(), idempotencyKey, fingerprint);

        if (!reservation.reserved() && !reservation.duplicate()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for a different action");
        }

        if (reservation.duplicate()) {
            RecoveryExecuteResponse res = new RecoveryExecuteResponse();
            res.setSuccess(true);
            res.setOutcomeCode("ALREADY_EXECUTED");
            return res;
        }

        try {
            idempotencyService.attachPayment(payment.getMerchant().getMerchantId(), idempotencyKey, payment, fingerprint);

            List<PaymentAttempt> attempts = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(paymentId);
            Provider provider = null;

            if ("REROUTE".equals(request.getAction()) && request.getTargetProvider() != null) {
                provider = providerRepository.findAll().stream()
                    .filter(p -> request.getTargetProvider().equals(p.getProviderCode()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid provider code"));
            } else if ("RETRY_NOW".equals(request.getAction())) {
                if (!attempts.isEmpty()) {
                    provider = attempts.get(attempts.size() - 1).getProvider();
                } else {
                    provider = providerRepository.findAll().get(0);
                }
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported action: " + request.getAction());
            }

            PaymentAttempt attempt = paymentService.executeRecoveryAttempt(payment.getMerchant().getMerchantId(), payment, provider, idempotencyKey + "-attempt", null);

            RecoveryExecuteResponse res = new RecoveryExecuteResponse();
            res.setSuccess(attempt.getStatus() == PaymentAttemptStatus.SUCCESS);
            res.setProviderUsed(provider.getProviderCode());
            res.setOutcomeCode(attempt.getStatus().name());
            res.setRecoveredAmount(res.isSuccess() ? payment.getAmount() : null);
            res.setExecutedAt(OffsetDateTime.now());

            idempotencyService.completeInRedis(payment.getMerchant().getMerchantId(), idempotencyKey, payment, fingerprint, 200, null);
            return res;
        } catch (Exception e) {
            idempotencyService.completeInRedis(payment.getMerchant().getMerchantId(), idempotencyKey, payment, fingerprint, 500, null);
            e.printStackTrace(); throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
