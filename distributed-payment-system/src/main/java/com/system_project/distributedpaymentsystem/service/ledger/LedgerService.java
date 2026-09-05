package com.system_project.distributedpaymentsystem.service.ledger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccount;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccountStatus;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccountType;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerEntry;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerEntryType;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransaction;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransactionStatus;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransactionType;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.exception.PaymentNotFoundException;
import com.system_project.distributedpaymentsystem.repository.LedgerAccountRepository;
import com.system_project.distributedpaymentsystem.repository.LedgerEntryRepository;
import com.system_project.distributedpaymentsystem.repository.LedgerTransactionRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;

@Service
public class LedgerService {

    private final PaymentRepository paymentRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(PaymentRepository paymentRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.paymentRepository = paymentRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerTransaction postSuccessfulPayment(PaymentLedgerEvent event) {
        validateEvent(event);

        return ledgerTransactionRepository
                .findByPayment_PaymentIdAndTransactionType(event.getPaymentId(), LedgerTransactionType.PAYMENT)
                .orElseGet(() -> createPaymentLedgerTransaction(event));
    }

    private LedgerTransaction createPaymentLedgerTransaction(PaymentLedgerEvent event) {
        Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.getPaymentId()));

        validatePaymentMatchesEvent(payment, event);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LedgerAccount debitAccount = findOrCreateFundingAccount(event.getCurrency(), now);
        LedgerAccount creditAccount = findOrCreateMerchantAccount(payment, event.getCurrency(), now);

        LedgerTransaction transaction = new LedgerTransaction(
                UUID.randomUUID(),
                payment,
                LedgerTransactionType.PAYMENT,
                event.getAmount(),
                "PAYMENT:" + event.getPaymentId(),
                event.getCurrency(),
                LedgerTransactionStatus.POSTED,
                now
        );

        LedgerTransaction savedTransaction;
        try {
            savedTransaction = ledgerTransactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            return ledgerTransactionRepository
                    .findByPayment_PaymentIdAndTransactionType(event.getPaymentId(), LedgerTransactionType.PAYMENT)
                    .orElseThrow(() -> ex);
        }

        List<LedgerEntry> entries = List.of(
                new LedgerEntry(UUID.randomUUID(), savedTransaction, debitAccount, LedgerEntryType.DEBIT,
                        event.getAmount(), event.getCurrency(), now),
                new LedgerEntry(UUID.randomUUID(), savedTransaction, creditAccount, LedgerEntryType.CREDIT,
                        event.getAmount(), event.getCurrency(), now)
        );
        validateBalanced(entries);
        ledgerEntryRepository.saveAll(entries);
        return savedTransaction;
    }

    private LedgerAccount findOrCreateFundingAccount(String currency, OffsetDateTime now) {
        return ledgerAccountRepository.findFirstByAccountTypeAndCurrencyAndStatus(
                        LedgerAccountType.CUSTOMER, currency, LedgerAccountStatus.ACTIVE)
                .orElseGet(() -> ledgerAccountRepository.save(new LedgerAccount(
                        UUID.randomUUID(),
                        null,
                        "Customer Funding " + currency,
                        LedgerAccountType.CUSTOMER,
                        currency,
                        LedgerAccountStatus.ACTIVE,
                        now,
                        now
                )));
    }

    private LedgerAccount findOrCreateMerchantAccount(Payment payment, String currency, OffsetDateTime now) {
        UUID merchantId = payment.getMerchant().getMerchantId();
        return ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                        merchantId, LedgerAccountType.MERCHANT, currency, LedgerAccountStatus.ACTIVE)
                .orElseGet(() -> ledgerAccountRepository.save(new LedgerAccount(
                        UUID.randomUUID(),
                        payment.getMerchant(),
                        "Merchant Settlement " + currency,
                        LedgerAccountType.MERCHANT,
                        currency,
                        LedgerAccountStatus.ACTIVE,
                        now,
                        now
                )));
    }

    private void validateEvent(PaymentLedgerEvent event) {
        if (event == null || event.getPaymentId() == null || event.getMerchantId() == null
                || event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || event.getCurrency() == null || event.getCurrency().isBlank()
                || !"SUCCESS".equalsIgnoreCase(event.getStatus())) {
            throw new IllegalArgumentException("Invalid successful payment ledger event");
        }
    }

    private void validatePaymentMatchesEvent(Payment payment, PaymentLedgerEvent event) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Payment is not confirmed successful: " + event.getPaymentId());
        }
        if (!payment.getMerchant().getMerchantId().equals(event.getMerchantId())) {
            throw new IllegalArgumentException("Payment merchant does not match ledger event");
        }
        if (payment.getAmount().compareTo(event.getAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount does not match ledger event");
        }
        if (!payment.getCurrency().equalsIgnoreCase(event.getCurrency())) {
            throw new IllegalArgumentException("Payment currency does not match ledger event");
        }
    }

    private void validateBalanced(List<LedgerEntry> entries) {
        BigDecimal debitTotal = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditTotal = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalStateException("Ledger transaction is not balanced");
        }
    }
}
