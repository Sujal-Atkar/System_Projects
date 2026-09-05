package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
        Optional<IdempotencyRecord> findByMerchant_MerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
    Optional<IdempotencyRecord> findByPayment_PaymentId(UUID paymentId);
}