package com.system_project.distributedpaymentsystem.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.PaymentAttempt;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findByPayment_PaymentIdOrderByAttemptNumberAsc(UUID paymentId);
}