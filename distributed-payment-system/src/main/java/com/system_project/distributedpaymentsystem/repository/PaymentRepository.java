package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByMerchant_MerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
}