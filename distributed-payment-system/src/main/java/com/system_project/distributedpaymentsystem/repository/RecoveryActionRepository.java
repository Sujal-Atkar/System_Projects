package com.system_project.distributedpaymentsystem.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryActionStatus;
import com.system_project.distributedpaymentsystem.service.recovery.RecoveryActionType;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, UUID> {
    Optional<RecoveryAction> findByActionId(UUID actionId);

    Optional<RecoveryAction> findTopByPayment_PaymentIdAndActionTypeAndStatusInOrderByAttemptNumberDesc(
            UUID paymentId,
            RecoveryActionType actionType,
            List<RecoveryActionStatus> statuses);
}
