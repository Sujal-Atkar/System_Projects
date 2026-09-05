package com.system_project.distributedpaymentsystem.api.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.system_project.distributedpaymentsystem.api.merchant.dto.MerchantOnboardingResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.ProvisionedApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialEnvironment;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.service.merchant.MerchantService;

@DisplayName("MerchantController Tests")
class MerchantControllerTest {

    private MerchantService merchantService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        merchantService = mock(MerchantService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantController(merchantService)).build();
    }

    @Test
    @DisplayName("Merchant creation should work without authentication")
    void merchantCreationShouldWorkWithoutAuthentication() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();

        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                credentialId,
                "test-key-id",
                "test-secret-plaintext",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        MerchantOnboardingResponse response = new MerchantOnboardingResponse(
                merchantId,
                "Test Merchant",
                "merchant@example.com",
                null,
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                credential
        );

        when(merchantService.createMerchantWithCredential(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantName\":\"Test Merchant\",\"email\":\"merchant@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$.email").value("merchant@example.com"));
    }

    @Test
    @DisplayName("Merchant creation response should include API credential")
    void merchantCreationResponseShouldIncludeCredential() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        String keyId = "test-key-id-12345";
        String plainSecret = "test-secret-plaintext-67890";

        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                credentialId,
                keyId,
                plainSecret,
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        MerchantOnboardingResponse response = new MerchantOnboardingResponse(
                merchantId,
                "Test Merchant",
                "merchant@example.com",
                null,
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                credential
        );

        when(merchantService.createMerchantWithCredential(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantName\":\"Test Merchant\",\"email\":\"merchant@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiCredential").exists())
                .andExpect(jsonPath("$.apiCredential.keyId").value(keyId))
                .andExpect(jsonPath("$.apiCredential.plaintextSecret").value(plainSecret))
                .andExpect(jsonPath("$.apiCredential.environment").value("TEST"))
                .andExpect(jsonPath("$.apiCredential.status").value("ACTIVE"));
    }
}