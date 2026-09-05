package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.RecoveryDecision;

public interface RecoveryDecisionRepository extends JpaRepository<RecoveryDecision, UUID> {

    Optional<RecoveryDecision> findByRisk_RiskId(UUID riskId);
}
