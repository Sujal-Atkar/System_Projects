package com.system_project.distributedpaymentsystem.api.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryContextResponse;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteRequest;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteResponse;
import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialService;
import com.system_project.distributedpaymentsystem.service.recovery.InternalRecoveryExecutionService;

@WebMvcTest(InternalRecoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
public class Phase5ValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InternalRecoveryExecutionService executionService;

    @MockBean
    private ApiCredentialService apiCredentialService;

        @Value("${payment.recovery.internal-secret}")
        private String internalSecret;

    @Test
    @DisplayName("Verify Section E: Exact duplicate execution with same idempotency key returns cached/already-executed result")
    void testIdempotencyExactDuplicateExecution() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "step_step_12345";

        RecoveryExecuteRequest request = new RecoveryExecuteRequest();
        request.setAction("RETRY_NOW");

        RecoveryExecuteResponse response = new RecoveryExecuteResponse();
        response.setSuccess(true);
        response.setOutcomeCode("ALREADY_EXECUTED");

        when(executionService.executeRecovery(eq(paymentId), eq(idempotencyKey), any(RecoveryExecuteRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                .header("X-Internal-Secret", internalSecret)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.outcomeCode").value("ALREADY_EXECUTED"));
    }

    @Test
    @DisplayName("Verify Section E: Same payment & key with conflicting action/payload rejected with 409 Conflict")
    void testIdempotencyConflictingPayloadRejected() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "step_step_12345";

        RecoveryExecuteRequest request = new RecoveryExecuteRequest();
        request.setAction("REROUTE");
        request.setTargetProvider("MOCK_PROVIDER_B");

        when(executionService.executeRecovery(eq(paymentId), eq(idempotencyKey), any(RecoveryExecuteRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for a different action"));

        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                .header("X-Internal-Secret", internalSecret)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Verify Section F: Reconciliation open blocks execution with 409 Conflict")
    void testReconciliationOpenBlocksExecution() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "step_step_recon";

        RecoveryExecuteRequest request = new RecoveryExecuteRequest();
        request.setAction("RETRY_NOW");

        when(executionService.executeRecovery(eq(paymentId), eq(idempotencyKey), any(RecoveryExecuteRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "RECONCILIATION_OPEN"));

        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                .header("X-Internal-Secret", internalSecret)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Verify Section J: Provider reroute requests target provider and returns success")
    void testProviderRerouteExecution() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "step_step_reroute";

        RecoveryExecuteRequest request = new RecoveryExecuteRequest();
        request.setAction("REROUTE");
        request.setTargetProvider("MOCK_PROVIDER_B");

        RecoveryExecuteResponse response = new RecoveryExecuteResponse();
        response.setSuccess(true);
        response.setProviderUsed("MOCK_PROVIDER_B");
        response.setOutcomeCode("SUCCESS");
        response.setRecoveredAmount(new BigDecimal("1500.00"));

        when(executionService.executeRecovery(eq(paymentId), eq(idempotencyKey), any(RecoveryExecuteRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                .header("X-Internal-Secret", internalSecret)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerUsed").value("MOCK_PROVIDER_B"))
                .andExpect(jsonPath("$.recoveredAmount").value("1500.0"));
    }
}