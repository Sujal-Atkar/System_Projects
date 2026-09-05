package com.system_project.distributedpaymentsystem.service.merchant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.system_project.distributedpaymentsystem.api.merchant.dto.CreateMerchantRequest;
import com.system_project.distributedpaymentsystem.api.merchant.dto.CreateMerchantResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.MerchantOnboardingResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.ProvisionedApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialEnvironment;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.repository.MerchantRepository;
import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialGenerationService;

@DisplayName("MerchantService Credential Provisioning Tests")
class MerchantServiceTest {

    private MerchantService merchantService;
    private MerchantRepository merchantRepository;
    private ApiCredentialGenerationService credentialGenerationService;

    @BeforeEach
    void setUp() {
        merchantRepository = mock(MerchantRepository.class);
        credentialGenerationService = mock(ApiCredentialGenerationService.class);
        merchantService = new MerchantService(merchantRepository, credentialGenerationService);
    }

    @Test
    @DisplayName("Should create merchant and provision TEST credential in same transaction")
    void testCreateMerchantWithCredential_provisionsCredential() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");
        request.setEmail("contact@acme.com");
        request.setWebhookUrl("https://acme.com/webhook");
        request.setPhone("+1234567890");

        UUID merchantId = UUID.randomUUID();
        Merchant savedMerchant = createMerchant(merchantId, "Acme Corp", "contact@acme.com");

        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                UUID.randomUUID(),
                "test-key-123",
                "test-secret-plaintext",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);
        when(credentialGenerationService.provisionTestCredential(any(Merchant.class))).thenReturn(credential);

        // When
        MerchantOnboardingResponse response = merchantService.createMerchantWithCredential(request);

        // Then
        assertNotNull(response);
        assertEquals(merchantId, response.getMerchantId());
        assertEquals("Acme Corp", response.getMerchantName());
        assertNotNull(response.getApiCredential());
        assertEquals(ApiCredentialEnvironment.TEST, response.getApiCredential().getEnvironment());
        assertEquals(ApiCredentialStatus.ACTIVE, response.getApiCredential().getStatus());
        verify(merchantRepository, times(1)).save(any(Merchant.class));
        verify(credentialGenerationService, times(1)).provisionTestCredential(any(Merchant.class));
    }

    @Test
    @DisplayName("Should return plaintext secret in onboarding response")
    void testCreateMerchantWithCredential_returnsPlaintextSecret() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");
        request.setEmail("contact@acme.com");
        request.setWebhookUrl("https://acme.com/webhook");
        request.setPhone("+1234567890");

        UUID merchantId = UUID.randomUUID();
        Merchant savedMerchant = createMerchant(merchantId, "Acme Corp", "contact@acme.com");

        String plainSecret = "secret-only-shown-once-12345";
        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                UUID.randomUUID(),
                "test-key-456",
                plainSecret,
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);
        when(credentialGenerationService.provisionTestCredential(any(Merchant.class))).thenReturn(credential);

        // When
        MerchantOnboardingResponse response = merchantService.createMerchantWithCredential(request);

        // Then
        assertEquals(plainSecret, response.getApiCredential().getPlaintextSecret());
    }

    @Test
    @DisplayName("Should return key ID in response for merchant to store")
    void testCreateMerchantWithCredential_returnsKeyId() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");
        request.setEmail("contact@acme.com");

        UUID merchantId = UUID.randomUUID();
        Merchant savedMerchant = createMerchant(merchantId, "Acme Corp", "contact@acme.com");

        String keyId = "pk_live_12345678_abcdefgh";
        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                UUID.randomUUID(),
                keyId,
                "secret",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);
        when(credentialGenerationService.provisionTestCredential(any(Merchant.class))).thenReturn(credential);

        // When
        MerchantOnboardingResponse response = merchantService.createMerchantWithCredential(request);

        // Then
        assertEquals(keyId, response.getApiCredential().getKeyId());
    }

    @Test
    @DisplayName("Should require merchantName")
    void testCreateMerchantWithCredential_requiresMerchantName() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setEmail("contact@acme.com");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            merchantService.createMerchantWithCredential(request);
        });
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(credentialGenerationService, never()).provisionTestCredential(any(Merchant.class));
    }

    @Test
    @DisplayName("Should require email")
    void testCreateMerchantWithCredential_requiresEmail() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            merchantService.createMerchantWithCredential(request);
        });
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(credentialGenerationService, never()).provisionTestCredential(any(Merchant.class));
    }

    @Test
    @DisplayName("Should trim whitespace from input fields")
    void testCreateMerchantWithCredential_trimsWhitespace() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("  Acme Corp  ");
        request.setEmail("  contact@acme.com  ");
        request.setWebhookUrl("  https://acme.com/webhook  ");
        request.setPhone("  +1234567890  ");

        UUID merchantId = UUID.randomUUID();
        Merchant savedMerchant = createMerchant(merchantId, "Acme Corp", "contact@acme.com");

        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                UUID.randomUUID(),
                "key",
                "secret",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant merchant = invocation.getArgument(0);
            assertEquals("Acme Corp", merchant.getMerchantName());
            assertEquals("contact@acme.com", merchant.getEmail());
            assertEquals("https://acme.com/webhook", merchant.getWebhookUrl());
            assertEquals("+1234567890", merchant.getPhone());
            return savedMerchant;
        });
        when(credentialGenerationService.provisionTestCredential(any(Merchant.class))).thenReturn(credential);

        // When
        MerchantOnboardingResponse response = merchantService.createMerchantWithCredential(request);

        // Then
        assertNotNull(response);
    }

    @Test
    @DisplayName("Should set merchant status to ACTIVE")
    void testCreateMerchantWithCredential_setsStatusActive() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");
        request.setEmail("contact@acme.com");

        UUID merchantId = UUID.randomUUID();
        Merchant savedMerchant = createMerchant(merchantId, "Acme Corp", "contact@acme.com");

        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                UUID.randomUUID(),
                "key",
                "secret",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);
        when(credentialGenerationService.provisionTestCredential(any(Merchant.class))).thenReturn(credential);

        // When
        MerchantOnboardingResponse response = merchantService.createMerchantWithCredential(request);

        // Then
        assertEquals(MerchantStatus.ACTIVE, response.getStatus());
    }

    @Test
    @DisplayName("Should include all merchant details in response")
    void testCreateMerchantWithCredential_includesAllMerchantFields() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");
        request.setEmail("contact@acme.com");
        request.setWebhookUrl("https://acme.com/webhook");
        request.setPhone("+1234567890");

        UUID merchantId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Merchant savedMerchant = new Merchant(
                merchantId,
                "Acme Corp",
                "contact@acme.com",
                "https://acme.com/webhook",
                MerchantStatus.ACTIVE,
                now,
                now,
                "+1234567890"
        );

        ProvisionedApiCredential credential = new ProvisionedApiCredential(
                UUID.randomUUID(),
                "key",
                "secret",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);
        when(credentialGenerationService.provisionTestCredential(any(Merchant.class))).thenReturn(credential);

        // When
        MerchantOnboardingResponse response = merchantService.createMerchantWithCredential(request);

        // Then
        assertEquals(merchantId, response.getMerchantId());
        assertEquals("Acme Corp", response.getMerchantName());
        assertEquals("contact@acme.com", response.getEmail());
        assertEquals("https://acme.com/webhook", response.getWebhookUrl());
        assertEquals("+1234567890", response.getPhone());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    @DisplayName("Legacy createMerchant should still work without provisioning credential")
    void testCreateMerchant_backwardCompatibility() {
        // Given
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setMerchantName("Acme Corp");
        request.setEmail("contact@acme.com");

        UUID merchantId = UUID.randomUUID();
        Merchant savedMerchant = createMerchant(merchantId, "Acme Corp", "contact@acme.com");

        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);

        // When
        CreateMerchantResponse response = merchantService.createMerchant(request);

        // Then
        assertNotNull(response);
        assertEquals(merchantId, response.getMerchantId());
        assertEquals("Acme Corp", response.getMerchantName());
        verify(credentialGenerationService, never()).provisionTestCredential(any(Merchant.class));
    }

    private Merchant createMerchant(UUID merchantId, String name, String email) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new Merchant(
                merchantId,
                name,
                email,
                "https://example.com/webhook",
                MerchantStatus.ACTIVE,
                now,
                now,
                "1234567890"
        );
    }
}
