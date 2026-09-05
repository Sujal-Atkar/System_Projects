package com.system_project.distributedpaymentsystem.domain.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "merchant_name", length = 150, nullable = false)
    private String merchantName;

    @Column(name = "email", length = 120, nullable = false, unique = true)
    private String email;

    @Column(name = "webhook_url", columnDefinition = "text")
    private String webhookUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "merchant_status", nullable = false)
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "phone", length = 20)
    private String phone;

    protected Merchant() {
    }

    public Merchant(UUID merchantId, String merchantName, String email, String webhookUrl,
            MerchantStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt, String phone) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.email = email;
        this.webhookUrl = webhookUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.phone = phone;
    }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public MerchantStatus getStatus() { return status; }
    public void setStatus(MerchantStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}