package com.system_project.distributedpaymentsystem.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.ReconciliationAttempt;

public interface ReconciliationAttemptRepository extends JpaRepository<ReconciliationAttempt, UUID> {
}
