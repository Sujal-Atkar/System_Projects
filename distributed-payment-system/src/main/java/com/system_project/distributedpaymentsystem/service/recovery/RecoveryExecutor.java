package com.system_project.distributedpaymentsystem.service.recovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttemptStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryActionStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResult;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResultOutcome;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.RecoveryActionRepository;
import com.system_project.distributedpaymentsystem.repository.RecoveryResultRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.service.payment.PaymentService;
import com.system_project.distributedpaymentsystem.service.provider.ProviderRouter;

@Service
public class RecoveryExecutor {

    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryResultRepository recoveryResultRepository;
    private final PaymentService paymentService;
    private final ProviderRouter providerRouter;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final RecoveryPolicy recoveryPolicy;
    private final int maxAttempts;

    @Autowired
    public RecoveryExecutor(RecoveryActionRepository recoveryActionRepository,
            RecoveryResultRepository recoveryResultRepository,
            PaymentService paymentService,
            ProviderRouter providerRouter,
            PaymentAttemptRepository paymentAttemptRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            RecoveryPolicy recoveryPolicy,
            @Value("${payment.recovery.max-attempts:3}") int maxAttempts) {
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryResultRepository = recoveryResultRepository;
        this.paymentService = paymentService;
        this.providerRouter = providerRouter;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.recoveryPolicy = recoveryPolicy;
        this.maxAttempts = maxAttempts;
    }

    public RecoveryExecutor(RecoveryActionRepository recoveryActionRepository,
            RecoveryResultRepository recoveryResultRepository,
            PaymentService paymentService,
            ProviderRouter providerRouter,
            PaymentAttemptRepository paymentAttemptRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            RecoveryPolicy recoveryPolicy) {
        this(recoveryActionRepository, recoveryResultRepository, paymentService, providerRouter,
                paymentAttemptRepository, reconciliationCaseRepository, recoveryPolicy, 3);
    }

    @Transactional
    public RecoveryResult execute(UUID actionId) {
        RecoveryAction action = recoveryActionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Recovery action not found: " + actionId));

        Optional<RecoveryResult> existing = recoveryResultRepository.findByAction(action);
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment payment = action.getPayment();
        if (payment == null) {
            throw new IllegalStateException("Recovery action has no payment: " + actionId);
        }

        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            throw new IllegalStateException("Recovery blocked while payment is UNKNOWN");
        }

        if (hasOpenReconciliationCase(payment)) {
            throw new IllegalStateException("Recovery blocked because an open reconciliation case exists");
        }

        if (attemptCount(payment) >= maxAttempts) {
            throw new IllegalStateException("Recovery blocked because max attempts have been reached");
        }

        if (action.getActionType() == null || action.getActionType() == RecoveryActionType.NO_ACTION) {
            throw new IllegalStateException("Recovery action is not executable: " + action.getActionType());
        }

        Provider provider = resolveProvider(action, payment);
        if (provider == null) {
            throw new IllegalStateException("No eligible provider available for recovery action: " + actionId);
        }

        ProviderProcessingResult failureResult = ProviderProcessingResult.failed(
                action.getActionType().name(),
                action.getActionType().name(),
                action.getFailureReason() != null ? action.getFailureReason() : "recovery execution guard");

        RecoveryPolicyResult policyResult = recoveryPolicy.evaluate(payment, provider, failureResult);
        if (policyResult.isBlocked() || (!policyResult.isRetryAllowed() && !policyResult.isRerouteAllowed())) {
            throw new IllegalStateException("Recovery policy rejected action: " + policyResult.getReason());
        }

        action.setStatus(RecoveryActionStatus.EXECUTING);
        action.setExecutedAt(OffsetDateTime.now());
        recoveryActionRepository.save(action);

        String syntheticIdempotencyKey = "RECOVERY:" + action.getActionId();
        PaymentAttempt recoveryAttempt = paymentService.executeRecoveryAttempt(
                payment.getMerchant().getMerchantId(),
                payment,
                provider,
                syntheticIdempotencyKey,
                action);

        if (recoveryAttempt == null) {
            action.setStatus(RecoveryActionStatus.CANCELLED);
            action.setFailureReason("Recovery attempt already executed or idempotency rejected");
            recoveryActionRepository.save(action);
            throw new IllegalStateException("Recovery attempt was not executed: " + actionId);
        }

        RecoveryResultOutcome outcome = switch (recoveryAttempt.getStatus()) {
            case SUCCESS -> RecoveryResultOutcome.RECOVERED;
            case FAILED -> RecoveryResultOutcome.NOT_RECOVERED;
            case TIMEOUT -> RecoveryResultOutcome.PARTIALLY_RECOVERED;
            default -> RecoveryResultOutcome.NOT_RECOVERED;
        };

        action.setStatus(recoveryAttempt.getStatus() == PaymentAttemptStatus.SUCCESS
                ? RecoveryActionStatus.SUCCESS
                : recoveryAttempt.getStatus() == PaymentAttemptStatus.TIMEOUT
                        ? RecoveryActionStatus.FAILED
                        : RecoveryActionStatus.FAILED);
        action.setCompletedAt(OffsetDateTime.now());
        action.setFailureReason(recoveryAttempt.getFailureReason());
        recoveryActionRepository.save(action);

        RecoveryResult recoveryResult = new RecoveryResult(
                UUID.randomUUID(),
                action,
                outcome,
                outcome == RecoveryResultOutcome.RECOVERED ? payment.getAmount() : BigDecimal.ZERO,
                payment.getCurrency(),
                payment,
                OffsetDateTime.now());
        recoveryResultRepository.save(recoveryResult);
        return recoveryResult;
    }

    private Provider resolveProvider(RecoveryAction action, Payment payment) {
        if (action.getActionType() == RecoveryActionType.RETRY) {
            if (action.getTargetProvider() != null) {
                return action.getTargetProvider();
            }
            List<PaymentAttempt> attempts = paymentAttemptRepository
                    .findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId());
            return attempts.isEmpty() ? providerRouter.selectProvider(payment.getMerchant().getMerchantId())
                    : attempts.get(attempts.size() - 1).getProvider();
        }

        if (action.getActionType() == RecoveryActionType.REROUTE) {
            return providerRouter.selectProvider(payment.getMerchant().getMerchantId());
        }

        if (action.getActionType() == RecoveryActionType.NOTIFY) {
            return null;
        }

        return null;
    }

    private boolean hasOpenReconciliationCase(Payment payment) {
        if (payment == null) {
            return false;
        }
        return reconciliationCaseRepository
                .findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(payment.getPaymentId(),
                        ReconciliationCaseStatus.OPEN)
                .isPresent();
    }

    private int attemptCount(Payment payment) {
        if (payment == null) {
            return 0;
        }
        List<PaymentAttempt> attempts = paymentAttemptRepository
                .findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId());
        return attempts == null ? 0 : attempts.size();
    }
}
