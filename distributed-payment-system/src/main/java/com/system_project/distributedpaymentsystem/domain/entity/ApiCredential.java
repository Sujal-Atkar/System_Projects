package com.system_project.distributedpaymentsystem.domain.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_credential")
public class ApiCredential {

    @Id
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "key_id", length = 100, nullable = false, unique = true)
    private String keyId;

    @Column(name = "secret_hash", length = 255, nullable = false)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "environment", columnDefinition = "api_credential_environment", nullable = false)
    private ApiCredentialEnvironment environment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "api_credential_status", nullable = false)
    private ApiCredentialStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    protected ApiCredential() {
    }

    public ApiCredential(UUID credentialId, Merchant merchant, String keyId, String secretHash,
            ApiCredentialEnvironment environment, ApiCredentialStatus status,
            OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime lastUsedAt) {
        this.credentialId = credentialId;
        this.merchant = merchant;
        this.keyId = keyId;
        this.secretHash = secretHash;
        this.environment = environment;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public ApiCredentialEnvironment getEnvironment() {
        return environment;
    }

    public ApiCredentialStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setStatus(ApiCredentialStatus status) {
        this.status = status;
    }

    public void setLastUsedAt(OffsetDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
