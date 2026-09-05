package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccount;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccountStatus;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccountType;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findFirstByAccountTypeAndCurrencyAndStatus(
            LedgerAccountType accountType, String currency, LedgerAccountStatus status);

    Optional<LedgerAccount> findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
            UUID merchantId, LedgerAccountType accountType, String currency, LedgerAccountStatus status);
}
