package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCase;
import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationCaseStatus;

public interface ReconciliationCaseRepository extends JpaRepository<ReconciliationCase, UUID> {
    Optional<ReconciliationCase> findByPayment_PaymentIdAndAttempt_AttemptId(UUID paymentId, UUID attemptId);

    Optional<ReconciliationCase> findTopByPayment_PaymentIdAndStatusOrderByCreatedAtDesc(UUID paymentId, ReconciliationCaseStatus status);
}
