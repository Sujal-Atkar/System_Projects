package com.system_project.distributedpaymentsystem.service.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccount;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccountStatus;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerAccountType;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerEntry;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerEntryType;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransaction;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransactionStatus;
import com.system_project.distributedpaymentsystem.domain.entity.LedgerTransactionType;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentType;
import com.system_project.distributedpaymentsystem.repository.LedgerAccountRepository;
import com.system_project.distributedpaymentsystem.repository.LedgerEntryRepository;
import com.system_project.distributedpaymentsystem.repository.LedgerTransactionRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private LedgerAccountRepository ledgerAccountRepository;

        @Mock
        private LedgerTransactionRepository ledgerTransactionRepository;

        @Mock
        private LedgerEntryRepository ledgerEntryRepository;

        private LedgerService ledgerService;

        @BeforeEach
        void setUp() {
                ledgerService = new LedgerService(paymentRepository, ledgerAccountRepository,
                                ledgerTransactionRepository, ledgerEntryRepository);
        }

        @Test
        void postSuccessfulPayment_shouldCreateTransactionWithDebitAndCreditEntries() {
                Payment payment = createPayment(PaymentStatus.SUCCESS);
                LedgerAccount fundingAccount = createAccount(LedgerAccountType.CUSTOMER, null, "USD");
                LedgerAccount merchantAccount = createAccount(LedgerAccountType.MERCHANT, payment.getMerchant(), "USD");

                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(
                                payment.getPaymentId(), LedgerTransactionType.PAYMENT)).thenReturn(Optional.empty());
                when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
                when(ledgerAccountRepository.findFirstByAccountTypeAndCurrencyAndStatus(
                                LedgerAccountType.CUSTOMER, "USD", LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(fundingAccount));
                when(ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                                payment.getMerchant().getMerchantId(), LedgerAccountType.MERCHANT, "USD",
                                LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(merchantAccount));
                when(ledgerTransactionRepository.saveAndFlush(any(LedgerTransaction.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(ledgerEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

                LedgerTransaction transaction = ledgerService.postSuccessfulPayment(createEvent(payment));

                assertNotNull(transaction);
                assertEquals(payment.getPaymentId(), transaction.getPayment().getPaymentId());
                assertEquals(LedgerTransactionType.PAYMENT, transaction.getTransactionType());

                ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
                verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
                List<LedgerEntry> entries = streamEntries(entriesCaptor.getValue());

                assertEquals(2, entries.size());
                assertEquals(1, entries.stream().filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                                .count());
                assertEquals(1, entries.stream().filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                                .count());
                assertEquals(new BigDecimal("100.00"), debitTotal(entries));
                assertEquals(new BigDecimal("100.00"), creditTotal(entries));
        }

        @Test
        void postSuccessfulPayment_shouldKeepDebitAndCreditBalanced() {
                Payment payment = createPayment(PaymentStatus.SUCCESS);
                mockSuccessfulPostingDependencies(payment);

                ledgerService.postSuccessfulPayment(createEvent(payment));

                ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
                verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
                List<LedgerEntry> entries = streamEntries(entriesCaptor.getValue());

                assertEquals(0, debitTotal(entries).compareTo(creditTotal(entries)));
        }

        @Test
        void postSuccessfulPayment_shouldReturnExistingTransactionForDuplicateEvent() {
                Payment payment = createPayment(PaymentStatus.SUCCESS);
                LedgerTransaction existing = createTransaction(payment);

                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(
                                payment.getPaymentId(), LedgerTransactionType.PAYMENT))
                                .thenReturn(Optional.of(existing));

                LedgerTransaction transaction = ledgerService.postSuccessfulPayment(createEvent(payment));

                assertEquals(existing.getTransactionId(), transaction.getTransactionId());
                verify(ledgerTransactionRepository, never()).saveAndFlush(any());
                verify(ledgerEntryRepository, never()).saveAll(any());
        }

        @Test
        void postSuccessfulPayment_shouldTreatUniqueConstraintCollisionAsDuplicateEvent() {
                Payment payment = createPayment(PaymentStatus.SUCCESS);
                LedgerTransaction existing = createTransaction(payment);
                LedgerAccount fundingAccount = createAccount(LedgerAccountType.CUSTOMER, null, "USD");
                LedgerAccount merchantAccount = createAccount(LedgerAccountType.MERCHANT, payment.getMerchant(), "USD");

                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(
                                payment.getPaymentId(), LedgerTransactionType.PAYMENT))
                                .thenReturn(Optional.empty(), Optional.of(existing));
                when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
                when(ledgerAccountRepository.findFirstByAccountTypeAndCurrencyAndStatus(
                                LedgerAccountType.CUSTOMER, "USD", LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(fundingAccount));
                when(ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                                payment.getMerchant().getMerchantId(), LedgerAccountType.MERCHANT, "USD",
                                LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(merchantAccount));
                when(ledgerTransactionRepository.saveAndFlush(any(LedgerTransaction.class)))
                                .thenThrow(new DataIntegrityViolationException("duplicate payment ledger transaction"));

                LedgerTransaction transaction = ledgerService.postSuccessfulPayment(createEvent(payment));

                assertEquals(existing.getTransactionId(), transaction.getTransactionId());
                verify(ledgerEntryRepository, never()).saveAll(any());
        }

        @Test
        void postSuccessfulPayment_shouldRejectUnconfirmedPayment() {
                Payment payment = createPayment(PaymentStatus.UNKNOWN);

                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(
                                payment.getPaymentId(), LedgerTransactionType.PAYMENT)).thenReturn(Optional.empty());
                when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));

                assertThrows(IllegalStateException.class,
                                () -> ledgerService.postSuccessfulPayment(createEvent(payment)));
                verify(ledgerEntryRepository, never()).saveAll(any());
        }

        @Test
        void postSuccessfulPayment_shouldPropagateEntryFailureForTransactionRollback() throws Exception {
                Payment payment = createPayment(PaymentStatus.SUCCESS);
                mockSuccessfulPostingDependencies(payment);
                when(ledgerEntryRepository.saveAll(any())).thenThrow(new RuntimeException("entry insert failed"));

                Method method = LedgerService.class.getMethod("postSuccessfulPayment", PaymentLedgerEvent.class);
                assertNotNull(method.getAnnotation(Transactional.class));
                assertThrows(RuntimeException.class, () -> ledgerService.postSuccessfulPayment(createEvent(payment)));
        }

        @Test
        void postSuccessfulPayment_shouldHandleConcurrentDuplicateEventsWithOneFinancialPosting() throws Exception {
                Payment payment = createPayment(PaymentStatus.SUCCESS);
                LedgerTransaction existing = createTransaction(payment);
                LedgerAccount fundingAccount = createAccount(LedgerAccountType.CUSTOMER, null, "USD");
                LedgerAccount merchantAccount = createAccount(LedgerAccountType.MERCHANT, payment.getMerchant(), "USD");
                CountDownLatch firstInsertStarted = new CountDownLatch(1);
                CountDownLatch releaseFirstInsert = new CountDownLatch(1);
                AtomicInteger inserts = new AtomicInteger();

                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(
                                payment.getPaymentId(), LedgerTransactionType.PAYMENT))
                                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(existing));
                when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
                when(ledgerAccountRepository.findFirstByAccountTypeAndCurrencyAndStatus(
                                LedgerAccountType.CUSTOMER, "USD", LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(fundingAccount));
                when(ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                                payment.getMerchant().getMerchantId(), LedgerAccountType.MERCHANT, "USD",
                                LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(merchantAccount));
                when(ledgerTransactionRepository.saveAndFlush(any(LedgerTransaction.class))).thenAnswer(invocation -> {
                        if (inserts.incrementAndGet() == 1) {
                                firstInsertStarted.countDown();
                                releaseFirstInsert.await(1, TimeUnit.SECONDS);
                                return invocation.getArgument(0);
                        }
                        firstInsertStarted.await(1, TimeUnit.SECONDS);
                        throw new DataIntegrityViolationException("duplicate payment ledger transaction");
                });
                when(ledgerEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

                CompletableFuture<LedgerTransaction> first = CompletableFuture.supplyAsync(
                                () -> ledgerService.postSuccessfulPayment(createEvent(payment)));
                firstInsertStarted.await(1, TimeUnit.SECONDS);
                CompletableFuture<LedgerTransaction> second = CompletableFuture.supplyAsync(
                                () -> ledgerService.postSuccessfulPayment(createEvent(payment)));
                releaseFirstInsert.countDown();

                first.join();
                second.join();

                verify(ledgerEntryRepository).saveAll(any());
        }

        @Test
        void postSuccessfulPayment_shouldCreateOneTransactionPerDifferentPayment() {
                Payment firstPayment = createPayment(PaymentStatus.SUCCESS);
                Payment secondPayment = createPayment(PaymentStatus.SUCCESS);
                LedgerAccount fundingAccount = createAccount(LedgerAccountType.CUSTOMER, null, "USD");
                LedgerAccount firstMerchantAccount = createAccount(LedgerAccountType.MERCHANT,
                                firstPayment.getMerchant(), "USD");
                LedgerAccount secondMerchantAccount = createAccount(LedgerAccountType.MERCHANT,
                                secondPayment.getMerchant(), "USD");

                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(any(), any()))
                                .thenReturn(Optional.empty());
                when(paymentRepository.findById(firstPayment.getPaymentId())).thenReturn(Optional.of(firstPayment));
                when(paymentRepository.findById(secondPayment.getPaymentId())).thenReturn(Optional.of(secondPayment));
                when(ledgerAccountRepository.findFirstByAccountTypeAndCurrencyAndStatus(
                                LedgerAccountType.CUSTOMER, "USD", LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(fundingAccount));
                when(ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                                firstPayment.getMerchant().getMerchantId(), LedgerAccountType.MERCHANT, "USD",
                                LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(firstMerchantAccount));
                when(ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                                secondPayment.getMerchant().getMerchantId(), LedgerAccountType.MERCHANT, "USD",
                                LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(secondMerchantAccount));
                when(ledgerTransactionRepository.saveAndFlush(any(LedgerTransaction.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(ledgerEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

                ledgerService.postSuccessfulPayment(createEvent(firstPayment));
                ledgerService.postSuccessfulPayment(createEvent(secondPayment));

                verify(ledgerTransactionRepository, org.mockito.Mockito.times(2)).saveAndFlush(any());
                verify(ledgerEntryRepository, org.mockito.Mockito.times(2)).saveAll(any());
        }

        private void mockSuccessfulPostingDependencies(Payment payment) {
                LedgerAccount fundingAccount = createAccount(LedgerAccountType.CUSTOMER, null, "USD");
                LedgerAccount merchantAccount = createAccount(LedgerAccountType.MERCHANT, payment.getMerchant(), "USD");
                when(ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(
                                payment.getPaymentId(), LedgerTransactionType.PAYMENT)).thenReturn(Optional.empty());
                when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
                when(ledgerAccountRepository.findFirstByAccountTypeAndCurrencyAndStatus(
                                LedgerAccountType.CUSTOMER, "USD", LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(fundingAccount));
                when(ledgerAccountRepository.findFirstByMerchant_MerchantIdAndAccountTypeAndCurrencyAndStatus(
                                payment.getMerchant().getMerchantId(), LedgerAccountType.MERCHANT, "USD",
                                LedgerAccountStatus.ACTIVE))
                                .thenReturn(Optional.of(merchantAccount));
                when(ledgerTransactionRepository.saveAndFlush(any(LedgerTransaction.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
        }

        private PaymentLedgerEvent createEvent(Payment payment) {
                return new PaymentLedgerEvent(payment.getPaymentId(), payment.getMerchant().getMerchantId(),
                                payment.getAmount(), payment.getCurrency(), "SUCCESS");
        }

        private Payment createPayment(PaymentStatus status) {
                Merchant merchant = new Merchant(UUID.randomUUID(), "Test Merchant",
                                "merchant-" + UUID.randomUUID() + "@test.com",
                                null, MerchantStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now(), null);
                return new Payment(UUID.randomUUID(), merchant, new BigDecimal("100.00"), "USD", status,
                                "idem-" + UUID.randomUUID(), "CUST-LEDGER", "ORDER-LEDGER", OffsetDateTime.now(),
                                OffsetDateTime.now(), PaymentType.PAYMENT, "ledger test");
        }

        private LedgerAccount createAccount(LedgerAccountType type, Merchant merchant, String currency) {
                return new LedgerAccount(UUID.randomUUID(), merchant, type.name() + " " + currency, type,
                                currency, LedgerAccountStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        }

        private LedgerTransaction createTransaction(Payment payment) {
                return new LedgerTransaction(UUID.randomUUID(), payment, LedgerTransactionType.PAYMENT,
                                payment.getAmount(), "PAYMENT:" + payment.getPaymentId(), payment.getCurrency(),
                                LedgerTransactionStatus.POSTED, OffsetDateTime.now());
        }

        private List<LedgerEntry> streamEntries(Iterable<LedgerEntry> entries) {
                return java.util.stream.StreamSupport.stream(entries.spliterator(), false).toList();
        }

        private BigDecimal debitTotal(List<LedgerEntry> entries) {
                return entries.stream()
                                .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                                .map(LedgerEntry::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal creditTotal(List<LedgerEntry> entries) {
                return entries.stream()
                                .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                                .map(LedgerEntry::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
}
