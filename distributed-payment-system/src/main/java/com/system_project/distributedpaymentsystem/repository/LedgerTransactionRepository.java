package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransaction;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransactionType;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByPayment_PaymentIdAndTransactionType(UUID paymentId,
            LedgerTransactionType transactionType);

    long countByPayment_PaymentId(UUID paymentId);
}
