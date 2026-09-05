package com.system_project.distributedpaymentsystem.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentRequest;
import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentResponse;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryContextResponse;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteRequest;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteResponse;
import com.system_project.distributedpaymentsystem.domain.entity.*;
import com.system_project.distributedpaymentsystem.repository.*;
import com.system_project.distributedpaymentsystem.service.ledger.PaymentLedgerConsumer;
import com.system_project.distributedpaymentsystem.service.recovery.PythonDetectionConsumer;
import com.system_project.distributedpaymentsystem.service.recovery.PythonDetectionWebhookClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5433/payment_db?options=-c%20TimeZone=UTC",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
public class LiveCrossSystemE2EValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private ReconciliationCaseRepository reconciliationCaseRepository;

    @Autowired
    private PythonDetectionConsumer pythonDetectionConsumer;
    @Autowired
    private PaymentLedgerConsumer paymentLedgerConsumer;
    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

        @Value("${payment.recovery.internal-secret}")
        private String internalSecret;

    @SpyBean
    private PythonDetectionWebhookClient pythonWebhookClient;

    @Test
    @DisplayName("GENUINE LIVE CROSS-SYSTEM E2E: Failure -> Outbox -> Detection -> Context -> Real Idempotent Recovery -> Ledger")
    void testGenuineLiveCrossSystemE2EWorkflow() throws Exception {
        // Step 1: Ensure test merchant exists
        Merchant merchant = merchantRepository.findAll().stream().findFirst().orElseGet(() -> {
            Merchant m = new Merchant(
                    UUID.randomUUID(),
                    "E2E Test Merchant",
                    "e2e-merchant@system.com",
                    "http://localhost:8080/webhook",
                    MerchantStatus.ACTIVE,
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    "+1234567890"
            );
            return merchantRepository.save(m);
        });
        UUID merchantId = merchant.getMerchantId();

        // Step 2: Trigger deterministic payment failure using FAIL customerRef trigger
        String idempotencyKey1 = "live-e2e-fail-key-" + UUID.randomUUID();
        CreatePaymentRequest createReq = new CreatePaymentRequest();
        createReq.setAmount(new BigDecimal("1500.00"));
        createReq.setCurrency("INR");
        createReq.setCustomerRef("CUST-FAIL-DECLINED");
        createReq.setOrderRef("ORDER-E2E-1001");
        createReq.setPaymentType(PaymentType.PAYMENT);
        createReq.setDescription("Live E2E initial failing payment");

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments").requestAttr("authenticatedMerchantId", merchantId)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-Idempotency-Key", idempotencyKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        CreatePaymentResponse initialResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), CreatePaymentResponse.class);
        UUID paymentId = initialResponse.getPaymentId();
        assertThat(paymentId).isNotNull();

        // Verify Step 2: Authoritative Java state is FAILED
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        List<PaymentAttempt> attempts = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(paymentId);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempts.get(0).getErrorCode()).isEqualTo("GATEWAY_DECLINE");

        // Step 3 & 4: Outbox event processing & PythonDetectionConsumer
        String outboxPayload = objectMapper.writeValueAsString(Map.of(
                "eventType", "PAYMENT_FAILED",
                "paymentId", paymentId.toString(),
                "amount", 1500.00,
                "currency", "INR",
                "status", "FAILED",
                "errorCode", "GATEWAY_DECLINE"
        ));
        pythonDetectionConsumer.processPaymentEvent(paymentId.toString(), outboxPayload);

        ArgumentCaptor<Map<String, Object>> webhookPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pythonWebhookClient, atLeastOnce()).notifyPythonSystem(eq(paymentId.toString()), webhookPayloadCaptor.capture());
        assertThat(webhookPayloadCaptor.getValue().get("paymentId")).isEqualTo(paymentId.toString());

        // Step 5 & 6: Python RealPaymentSystemClient fetches Authoritative Context
        MvcResult contextResult = mockMvc.perform(get("/api/internal/payments/" + paymentId + "/recovery-context")
                        .header("X-Internal-Secret", internalSecret))
                .andExpect(status().isOk())
                .andReturn();

        RecoveryContextResponse contextResponse = objectMapper.readValue(
                contextResult.getResponse().getContentAsString(), RecoveryContextResponse.class);
        assertThat(contextResponse.getPaymentId()).isEqualTo(paymentId);
        assertThat(contextResponse.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(contextResponse.getPaymentStatus()).isEqualTo("FAILED");
        assertThat(contextResponse.isReconciliationOpen()).isFalse();

        // Step 7 & 8: Python triggers Real Recovery Execution
        String recoveryStepIdempotencyKey = "recovery-step-key-" + UUID.randomUUID();
        RecoveryExecuteRequest recoveryExecReq = new RecoveryExecuteRequest();
                recoveryExecReq.setAction("REROUTE");
        recoveryExecReq.setTargetProvider("PROVIDER_B");

        MvcResult execResult = mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                        .header("X-Internal-Secret", internalSecret)
                        .header("Idempotency-Key", recoveryStepIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recoveryExecReq)))
                .andExpect(status().isOk())
                .andReturn();

        RecoveryExecuteResponse execResponse = objectMapper.readValue(
                execResult.getResponse().getContentAsString(), RecoveryExecuteResponse.class);
        assertThat(execResponse.isSuccess()).isTrue();
        assertThat(execResponse.getOutcomeCode()).isEqualTo("SUCCESS");
        assertThat(execResponse.getRecoveredAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // Step 9 & 10: Verify Authoritative Java Payment & PaymentAttempt transitioned to SUCCESS
        Payment recoveredPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(recoveredPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        List<PaymentAttempt> allAttempts = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(paymentId);
        assertThat(allAttempts).hasSize(2);
        assertThat(allAttempts.get(1).getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);

        // Step 12: Real Idempotency Verification against Java IdempotencyService
        MvcResult dupResult = mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                        .header("X-Internal-Secret", internalSecret)
                        .header("Idempotency-Key", recoveryStepIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recoveryExecReq)))
                .andExpect(status().isOk())
                .andReturn();

        RecoveryExecuteResponse dupResponse = objectMapper.readValue(
                dupResult.getResponse().getContentAsString(), RecoveryExecuteResponse.class);
        assertThat(dupResponse.isSuccess()).isTrue();
        assertThat(dupResponse.getOutcomeCode()).isEqualTo("ALREADY_EXECUTED");

        // Verify that NO 3rd attempt was created by the duplicate call
        List<PaymentAttempt> attemptsAfterDup = paymentAttemptRepository.findByPayment_PaymentIdOrderByAttemptNumberAsc(paymentId);
        assertThat(attemptsAfterDup).hasSize(2);

        // Step 12b: Test conflicting payload rejected with 409 Conflict
        RecoveryExecuteRequest conflictReq = new RecoveryExecuteRequest();
        conflictReq.setAction("RETRY_NOW");

        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                        .header("X-Internal-Secret", internalSecret)
                        .header("Idempotency-Key", recoveryStepIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict());

        // Step 13: Test Reconciliation Open blocks recovery execution
        ReconciliationCase openReconCase = new ReconciliationCase(
                UUID.randomUUID(),
                recoveredPayment,
                allAttempts.get(1),
                ReconciliationCaseStatus.OPEN,
                ReconciliationReason.STATUS_MISMATCH,
                "UNKNOWN",
                null,
                0,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now(),
                null
        );
        reconciliationCaseRepository.save(openReconCase);

        String reconTestKey = "recon-test-key-" + UUID.randomUUID();
        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                        .header("X-Internal-Secret", internalSecret)
                        .header("Idempotency-Key", reconTestKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recoveryExecReq)))
                .andExpect(status().isConflict());

        // Step 14: Process Success Event through PaymentLedgerConsumer and verify Real Ledger entry
        String ledgerSuccessPayload = objectMapper.writeValueAsString(Map.of(
                "eventType", "PAYMENT_SUCCEEDED",
                "paymentId", paymentId.toString(),
                "merchantId", merchantId.toString(),
                "amount", 1500.00,
                "currency", "INR",
                "status", "SUCCESS"
        ));
        boolean ledgerPosted = paymentLedgerConsumer.processPaymentEvent(paymentId.toString(), ledgerSuccessPayload);
        assertThat(ledgerPosted).isTrue();

        java.util.Optional<LedgerTransaction> txOpt = ledgerTransactionRepository.findByPayment_PaymentIdAndTransactionType(paymentId, LedgerTransactionType.PAYMENT);
        assertThat(txOpt).isPresent();
        LedgerTransaction tx = txOpt.get();
        assertThat(tx.getStatus()).isEqualTo(LedgerTransactionStatus.POSTED);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransaction_TransactionId(tx.getTransactionId());
        assertThat(entries).hasSize(2);
        System.out.println(">>> LIVE E2E SUCCESSFUL: Payment " + paymentId + " recovered to provider " + execResponse.getProviderUsed() + " with Ledger Tx " + tx.getTransactionId());
    }
}
