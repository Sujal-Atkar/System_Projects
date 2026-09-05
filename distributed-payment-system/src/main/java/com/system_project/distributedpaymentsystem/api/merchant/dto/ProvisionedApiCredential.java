package com.system_project.distributedpaymentsystem.api.merchant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialEnvironment;
import com.system_project.distributedpaymentsystem.domain.entity.ApiCredentialStatus;

public class ProvisionedApiCredential {

    private UUID credentialId;
    private String keyId;
    private String plaintextSecret;
    private ApiCredentialEnvironment environment;
    private ApiCredentialStatus status;
    private OffsetDateTime createdAt;

    public ProvisionedApiCredential() {
    }

    public ProvisionedApiCredential(UUID credentialId, String keyId, String plaintextSecret,
            ApiCredentialEnvironment environment, ApiCredentialStatus status, OffsetDateTime createdAt) {
        this.credentialId = credentialId;
        this.keyId = keyId;
        this.plaintextSecret = plaintextSecret;
        this.environment = environment;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getPlaintextSecret() { return plaintextSecret; }
    public void setPlaintextSecret(String plaintextSecret) { this.plaintextSecret = plaintextSecret; }

    public ApiCredentialEnvironment getEnvironment() { return environment; }
    public void setEnvironment(ApiCredentialEnvironment environment) { this.environment = environment; }

    public ApiCredentialStatus getStatus() { return status; }
    public void setStatus(ApiCredentialStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
