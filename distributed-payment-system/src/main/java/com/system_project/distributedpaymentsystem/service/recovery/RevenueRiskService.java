package com.system_project.distributedpaymentsystem.service.recovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRiskStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RiskType;
import com.system_project.distributedpaymentsystem.repository.RevenueRiskRepository;

@Service
public class RevenueRiskService {

    private final RevenueRiskRepository revenueRiskRepository;

    public RevenueRiskService(RevenueRiskRepository revenueRiskRepository) {
        this.revenueRiskRepository = revenueRiskRepository;
    }

    /**
     * Idempotently create a RevenueRisk for a FAILED payment.
     *
     * Only FAILED payments are eligible. If a RevenueRisk already exists for this
     * payment, it is reused (idempotent behavior for Kafka redelivery).
     *
     * @param payment the payment that failed
     * @return Optional containing the RevenueRisk if created/found, or empty if ineligible
     */
    @Transactional
    public Optional<RevenueRisk> createIfAbsent(Payment payment) {
        // Only create for definitive FAILED payments
        if (payment.getStatus() != PaymentStatus.FAILED) {
            return Optional.empty();
        }

        // Check if a RevenueRisk already exists for this payment
        Optional<RevenueRisk> existing = revenueRiskRepository.findByPayment_PaymentId(payment.getPaymentId());
        if (existing.isPresent()) {
            return existing; // Reuse existing risk (idempotent)
        }

        // Create a new RevenueRisk in DETECTED state
        RevenueRisk newRisk = new RevenueRisk(
                UUID.randomUUID(),
                payment.getMerchant(),
                payment,
                RiskType.PAYMENT_FAILURE,
                payment.getAmount(),
                payment.getCurrency(),
                BigDecimal.valueOf(1.0), // Default risk score for newly detected risks
                "Payment failed and is eligible for recovery",
                RevenueRiskStatus.DETECTED,
                OffsetDateTime.now(),
                null
        );

        RevenueRisk savedRisk = revenueRiskRepository.save(newRisk);
        return Optional.of(savedRisk);
    }

    /**
     * Atomically claim a RevenueRisk for decision processing.
     *
     * Performs an atomic database-level transition: DETECTED → IN_RECOVERY.
     * Only one concurrent caller can successfully claim the risk. Other callers
     * will fail (claim already processed).
     *
     * @param riskId the ID of the risk to claim
     * @return ClaimResult indicating success or failure with reason
     */
    @Transactional
    public ClaimResult claimForDecision(UUID riskId) {
        if (riskId == null) {
            return ClaimResult.failure("Risk ID is required");
        }

        // Attempt atomic status transition at database level
        int rowsUpdated = revenueRiskRepository.updateStatusIfCurrent(
                riskId,
                RevenueRiskStatus.DETECTED,
                RevenueRiskStatus.IN_RECOVERY
        );

        if (rowsUpdated > 0) {
            // Claim succeeded: one row was updated
            return ClaimResult.success();
        } else {
            // Claim failed: risk not found or already claimed (not in DETECTED state)
            return ClaimResult.failure("Risk is not available for claim (not in DETECTED state or does not exist)");
        }
    }
}
