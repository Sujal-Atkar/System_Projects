package com.system_project.distributedpaymentsystem.service.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCase;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;
import com.system_project.distributedpaymentsystem.exception.NoEligibleProviderException;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.PaymentAttemptRepository;
import com.system_project.distributedpaymentsystem.repository.ReconciliationCaseRepository;
import com.system_project.distributedpaymentsystem.service.provider.ProviderCircuitState;
import com.system_project.distributedpaymentsystem.service.provider.ProviderHealthService;
import com.system_project.distributedpaymentsystem.service.provider.ProviderRouter;

@Service
public class RecoveryPolicy {

    private final ProviderHealthService providerHealthService;
    private final ProviderRouter providerRouter;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final int maxAttempts;

    public RecoveryPolicy(ProviderHealthService providerHealthService,
            ProviderRouter providerRouter,
            PaymentAttemptRepository paymentAttemptRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            @Value("${payment.recovery.max-attempts:3}") int maxAttempts) {
        this.providerHealthService = providerHealthService;
        this.providerRouter = providerRouter;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.maxAttempts = maxAttempts;
    }

    public RecoveryPolicyResult evaluate(Payment payment, Provider provider, ProviderProcessingResult result) {
        if (payment == null) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.UNKNOWN, RecoveryActionType.NO_ACTION,
                    false, false, true);
        }

        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.RECONCILIATION_OPEN,
                    RecoveryActionType.NO_ACTION, false, false, true);
        }

        if (hasOpenReconciliationCase(payment)) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.RECONCILIATION_OPEN,
                    RecoveryActionType.NO_ACTION, false, false, true);
        }

        if (result == null) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.UNKNOWN, RecoveryActionType.NO_ACTION,
                    false, false, true);
        }

        if (result.isSuccess()) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.UNKNOWN, RecoveryActionType.NO_ACTION,
                    false, false, false);
        }

        if (!result.isFailed() && !result.isTimeout()) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.UNKNOWN, RecoveryActionType.NO_ACTION,
                    false, false, false);
        }

        if (attemptCount(payment) >= maxAttempts) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.MAX_ATTEMPTS_REACHED,
                    RecoveryActionType.NO_ACTION, false, false, true);
        }

        if (isBusinessFailure(result)) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.BUSINESS_FAILURE,
                    RecoveryActionType.NO_ACTION, false, false, true);
        }

        ProviderCircuitState state = providerHealthService != null && provider != null
                ? providerHealthService.getState(provider)
                : ProviderCircuitState.CLOSED;

        if (state == ProviderCircuitState.OPEN) {
            return evaluateReroute(payment, provider);
        }

        return new RecoveryPolicyResult(RecoveryPolicyReason.TECHNICAL_TRANSIENT_FAILURE,
                RecoveryActionType.RETRY, true, false, false);
    }

    private RecoveryPolicyResult evaluateReroute(Payment payment, Provider provider) {
        if (provider == null || payment == null) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.PROVIDER_UNAVAILABLE,
                    RecoveryActionType.NO_ACTION, false, false, true);
        }

        try {
            Provider alternate = providerRouter.selectProvider(payment.getMerchant().getMerchantId());
            if (alternate != null && !alternate.getProviderId().equals(provider.getProviderId())) {
                return new RecoveryPolicyResult(RecoveryPolicyReason.PROVIDER_UNAVAILABLE,
                        RecoveryActionType.REROUTE, false, true, false);
            }
        } catch (NoEligibleProviderException e) {
            return new RecoveryPolicyResult(RecoveryPolicyReason.PROVIDER_UNAVAILABLE,
                    RecoveryActionType.NO_ACTION, false, false, true);
        }

        return new RecoveryPolicyResult(RecoveryPolicyReason.PROVIDER_UNAVAILABLE,
                RecoveryActionType.NO_ACTION, false, false, true);
    }

    private boolean hasOpenReconciliationCase(Payment payment) {
        if (payment == null) {
            return false;
        }

        Optional<ReconciliationCase> openCase = reconciliationCaseRepository
                .findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(payment.getPaymentId(), ReconciliationCaseStatus.OPEN);
        return openCase.isPresent();
    }

    private int attemptCount(Payment payment) {
        if (payment == null) {
            return 0;
        }

        List<PaymentAttempt> attempts = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(payment.getPaymentId());
        return attempts == null ? 0 : attempts.size();
    }

    private boolean isBusinessFailure(ProviderProcessingResult result) {
        String code = result.getErrorCode();
        if (code == null) {
            return false;
        }

        String normalized = code.toUpperCase();
        return normalized.contains("DECLINED")
                || normalized.contains("CARD")
                || normalized.contains("INVALID")
                || normalized.contains("FRAUD")
                || normalized.contains("INSUFFICIENT")
                || normalized.contains("BLOCKED");
    }
}
