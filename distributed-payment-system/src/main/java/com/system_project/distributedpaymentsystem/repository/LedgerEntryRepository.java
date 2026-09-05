package com.system_project.distributedpaymentsystem.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransaction_TransactionId(UUID transactionId);

    long countByTransaction_TransactionId(UUID transactionId);
}
