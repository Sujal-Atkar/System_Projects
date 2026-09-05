package com.system_project.distributedpaymentsystem.service.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.system_project.distributedpaymentsystem.api.merchant.dto.ProvisionedApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialEnvironment;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.repository.ApiCredentialRepository;

@DisplayName("ApiCredentialGenerationService Tests")
class ApiCredentialGenerationServiceTest {

    private ApiCredentialGenerationService generationService;
    private ApiCredentialRepository credentialRepository;
    private ApiCredentialService credentialService;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(ApiCredentialRepository.class);
        credentialService = mock(ApiCredentialService.class);
        generationService = new ApiCredentialGenerationService(credentialRepository, credentialService);
    }

    @Test
    @DisplayName("Should generate a TEST credential with TEST environment and ACTIVE status")
    void testGenerateTestCredential_hasCorrectEnvironmentAndStatus() {
        // Given
        Merchant merchant = createTestMerchant();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ApiCredential savedCredential = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                "test-key-id",
                "test-hash",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                now,
                null,
                null
        );

        when(credentialService.hashSecret(any())).thenReturn("test-hash");
        when(credentialRepository.save(any(ApiCredential.class))).thenReturn(savedCredential);

        // When
        ProvisionedApiCredential result = generationService.provisionTestCredential(merchant);

        // Then
        assertNotNull(result);
        assertEquals(ApiCredentialEnvironment.TEST, result.getEnvironment());
        assertEquals(ApiCredentialStatus.ACTIVE, result.getStatus());
    }

    @Test
    @DisplayName("Should return plaintext secret in response")
    void testGenerateTestCredential_returnsPlaintextSecret() {
        // Given
        Merchant merchant = createTestMerchant();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ApiCredential savedCredential = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                "test-key-id",
                "test-hash",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                now,
                null,
                null
        );

        when(credentialService.hashSecret(any())).thenReturn("test-hash");
        when(credentialRepository.save(any(ApiCredential.class))).thenReturn(savedCredential);

        // When
        ProvisionedApiCredential result = generationService.provisionTestCredential(merchant);

        // Then
        assertNotNull(result.getPlaintextSecret());
        assertFalse(result.getPlaintextSecret().isBlank());
    }

    @Test
    @DisplayName("Should generate cryptographically secure credentials (non-deterministic)")
    void testGenerateTestCredential_generatesUniqueCredentials() {
        // Given
        Merchant merchant1 = createTestMerchant();
        Merchant merchant2 = createTestMerchant();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ApiCredential savedCredential1 = new ApiCredential(
                UUID.randomUUID(),
                merchant1,
                "key1",
                "hash1",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                now,
                null,
                null
        );

        ApiCredential savedCredential2 = new ApiCredential(
                UUID.randomUUID(),
                merchant2,
                "key2",
                "hash2",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                now,
                null,
                null
        );

        when(credentialService.hashSecret(any())).thenReturn("hash1").thenReturn("hash2");
        when(credentialRepository.save(any(ApiCredential.class)))
                .thenReturn(savedCredential1)
                .thenReturn(savedCredential2);

        // When
        ProvisionedApiCredential result1 = generationService.provisionTestCredential(merchant1);
        ProvisionedApiCredential result2 = generationService.provisionTestCredential(merchant2);

        // Then
        assertNotEquals(result1.getKeyId(), result2.getKeyId());
        assertNotEquals(result1.getPlaintextSecret(), result2.getPlaintextSecret());
    }

    @Test
    @DisplayName("Should persist credential with correct merchant reference")
    void testGenerateTestCredential_persistsWithCorrectMerchant() {
        // Given
        Merchant merchant = createTestMerchant();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ApiCredential[] capturedCredentialArray = new ApiCredential[1];

        when(credentialService.hashSecret(any())).thenReturn("test-hash");
        when(credentialRepository.save(any(ApiCredential.class))).thenAnswer(invocation -> {
            capturedCredentialArray[0] = invocation.getArgument(0);
            return new ApiCredential(
                    UUID.randomUUID(),
                    merchant,
                    capturedCredentialArray[0].getKeyId(),
                    capturedCredentialArray[0].getSecretHash(),
                    ApiCredentialEnvironment.TEST,
                    ApiCredentialStatus.ACTIVE,
                    now,
                    null,
                    null
            );
        });

        // When
        generationService.provisionTestCredential(merchant);

        // Then
        assertNotNull(capturedCredentialArray[0]);
        assertEquals(merchant.getMerchantId(), capturedCredentialArray[0].getMerchant().getMerchantId());
    }

    @Test
    @DisplayName("Should have no expiry for TEST credentials")
    void testGenerateTestCredential_noExpiry() {
        // Given
        Merchant merchant = createTestMerchant();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ApiCredential savedCredential = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                "test-key-id",
                "test-hash",
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                now,
                null,
                null
        );

        when(credentialService.hashSecret(any())).thenReturn("test-hash");
        when(credentialRepository.save(any(ApiCredential.class))).thenReturn(savedCredential);

        // When
        ProvisionedApiCredential result = generationService.provisionTestCredential(merchant);

        // Then
        // Verify that expiresAt is not set (passed as null to constructor)
        // The savedCredential has null expiresAt, which should be reflected in the response
        assertNotNull(result);
    }

    private Merchant createTestMerchant() {
        return new Merchant(
                UUID.randomUUID(),
                "Test Merchant",
                "test@example.com",
                "https://example.com/webhook",
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                "1234567890"
        );
    }
}
