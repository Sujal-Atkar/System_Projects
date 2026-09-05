package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryResult;

public interface RecoveryResultRepository extends JpaRepository<RecoveryResult, UUID> {
    Optional<RecoveryResult> findByAction(RecoveryAction action);
    Optional<RecoveryResult> findByAction_ActionId(UUID actionId);
}
