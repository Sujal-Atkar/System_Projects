package com.system_project.distributedpaymentsystem.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.system_project.distributedpaymentsystem.api.merchant.dto.ProvisionedApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialEnvironment;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.repository.ApiCredentialRepository;

@Service
public class ApiCredentialGenerationService {

    private static final int KEY_ID_LENGTH = 32;
    private static final int SECRET_LENGTH = 48;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final ApiCredentialRepository apiCredentialRepository;
    private final ApiCredentialService apiCredentialService;

    public ApiCredentialGenerationService(
            ApiCredentialRepository apiCredentialRepository,
            ApiCredentialService apiCredentialService) {
        this.apiCredentialRepository = apiCredentialRepository;
        this.apiCredentialService = apiCredentialService;
    }

    /**
     * Provision a TEST API credential for a newly created merchant.
     * Returns the plaintext secret only once (for display to merchant during onboarding).
     * The secret is never persisted in plaintext.
     *
     * @param merchant the merchant to provision for
     * @return credential information including the plaintext secret
     */
    public ProvisionedApiCredential provisionTestCredential(Merchant merchant) {
        String keyId = generateKeyId();
        String plaintextSecret = generateSecret();
        String secretHash = apiCredentialService.hashSecret(plaintextSecret);

        ApiCredential credential = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                keyId,
                secretHash,
                ApiCredentialEnvironment.TEST,
                ApiCredentialStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC),
                null, // no expiration for test credentials
                null
        );

        ApiCredential savedCredential = apiCredentialRepository.save(credential);

        return new ProvisionedApiCredential(
                savedCredential.getCredentialId(),
                savedCredential.getKeyId(),
                plaintextSecret,
                savedCredential.getEnvironment(),
                savedCredential.getStatus(),
                savedCredential.getCreatedAt()
        );
    }

    private String generateKeyId() {
        byte[] randomBytes = new byte[KEY_ID_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generateSecret() {
        byte[] randomBytes = new byte[SECRET_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
