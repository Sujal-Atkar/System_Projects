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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryContextResponse;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteRequest;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteResponse;
import com.system_project.distributedpaymentsystem.service.recovery.InternalRecoveryExecutionService;
import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialService;

@WebMvcTest(InternalRecoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
public class InternalRecoveryControllerTest {

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
    void getContext_missingSecret_unauthorized() throws Exception {
        mockMvc.perform(get("/api/internal/payments/" + UUID.randomUUID() + "/recovery-context"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getContext_validSecret_success() throws Exception {
        UUID paymentId = UUID.randomUUID();
        RecoveryContextResponse response = new RecoveryContextResponse();
        response.setPaymentId(paymentId);
        response.setAmount(new BigDecimal("5000.00"));

        when(executionService.getContext(paymentId)).thenReturn(response);

        mockMvc.perform(get("/api/internal/payments/" + paymentId + "/recovery-context")
                .header("X-Internal-Secret", internalSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("5000.0"));
    }

    @Test
    void executeRecovery_validSecret_success() throws Exception {
        UUID paymentId = UUID.randomUUID();
        RecoveryExecuteRequest request = new RecoveryExecuteRequest();
        request.setAction("REROUTE");
        request.setTargetProvider("MOCK_PROVIDER_B");

        RecoveryExecuteResponse response = new RecoveryExecuteResponse();
        response.setSuccess(true);
        response.setOutcomeCode("SUCCESS");

        when(executionService.executeRecovery(eq(paymentId), eq("idemp-key-123"), any(RecoveryExecuteRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/internal/payments/" + paymentId + "/recovery-execute")
                .header("X-Internal-Secret", internalSecret)
                .header("Idempotency-Key", "idemp-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.outcomeCode").value("SUCCESS"));
    }
}
