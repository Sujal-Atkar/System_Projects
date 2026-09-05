package com.system_project.distributedpaymentsystem.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.system_project.distributedpaymentsystem.domain.entity.ApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;
import com.system_project.distributedpaymentsystem.exception.InvalidApiKeyException;
import com.system_project.distributedpaymentsystem.repository.ApiCredentialRepository;

@Service
public class ApiCredentialService {

    private final ApiCredentialRepository apiCredentialRepository;

    public ApiCredentialService(ApiCredentialRepository apiCredentialRepository) {
        this.apiCredentialRepository = apiCredentialRepository;
    }

    public ApiCredential authenticate(String keyId, String secret) {
        if (keyId == null || keyId.isBlank()) {
            throw new InvalidApiKeyException("API key is required");
        }
        if (secret == null || secret.isBlank()) {
            throw new InvalidApiKeyException("API secret is required");
        }

        ApiCredential credential = apiCredentialRepository.findByKeyId(keyId.trim())
                .orElseThrow(InvalidApiKeyException::new);

        if (credential.getStatus() != ApiCredentialStatus.ACTIVE) {
            throw new InvalidApiKeyException("API credential is revoked");
        }

        if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidApiKeyException("API credential has expired");
        }

        if (!hashSecret(secret).equalsIgnoreCase(credential.getSecretHash())) {
            throw new InvalidApiKeyException();
        }

        credential.setLastUsedAt(OffsetDateTime.now());
        apiCredentialRepository.save(credential);
        return credential;
    }

    public String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public UUID resolveMerchantId(String keyId, String secret) {
        return authenticate(keyId, secret).getMerchant().getMerchantId();
    }
}
