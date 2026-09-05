package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRiskStatus;

public interface RevenueRiskRepository extends JpaRepository<RevenueRisk, UUID> {

    Optional<RevenueRisk> findByPayment_PaymentId(UUID paymentId);

    @Modifying
    @Transactional
    @Query("UPDATE RevenueRisk r SET r.status = :newStatus WHERE r.riskId = :riskId AND r.status = :currentStatus")
    int updateStatusIfCurrent(
            @Param("riskId") UUID riskId,
            @Param("currentStatus") RevenueRiskStatus currentStatus,
            @Param("newStatus") RevenueRiskStatus newStatus);
}
