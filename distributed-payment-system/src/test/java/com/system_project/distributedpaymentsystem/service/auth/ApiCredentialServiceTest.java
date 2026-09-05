package com.system_project.distributedpaymentsystem.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.system_project.distributedpaymentsystem.domain.entity.ApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialEnvironment;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.exception.InvalidApiKeyException;
import com.system_project.distributedpaymentsystem.repository.ApiCredentialRepository;

@ExtendWith(MockitoExtension.class)
class ApiCredentialServiceTest {

    @Mock
    private ApiCredentialRepository apiCredentialRepository;

    private ApiCredentialService apiCredentialService;

    @BeforeEach
    void setUp() {
        apiCredentialService = new ApiCredentialService(apiCredentialRepository);
    }

    @Test
    void authenticate_shouldReturnCredentialForValidApiKeyAndSecret() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        ApiCredential credential = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                "key-123",
                apiCredentialService.hashSecret("super-secret"),
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now().minusDays(1),
                null,
                null
        );

        when(apiCredentialRepository.findByKeyId("key-123")).thenReturn(Optional.of(credential));

        ApiCredential authenticated = apiCredentialService.authenticate("key-123", "super-secret");

        assertEquals(merchantId, authenticated.getMerchant().getMerchantId());
    }

    @Test
    void authenticate_shouldRejectRevokedOrExpiredCredential() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                merchantId,
                "Test Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        ApiCredential revoked = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                "revoked-key",
                apiCredentialService.hashSecret("secret"),
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.REVOKED,
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1),
                null
        );

        when(apiCredentialRepository.findByKeyId("revoked-key")).thenReturn(Optional.of(revoked));

        assertThrows(InvalidApiKeyException.class, () -> apiCredentialService.authenticate("revoked-key", "secret"));
    }
}
