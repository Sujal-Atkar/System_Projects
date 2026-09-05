package com.system_project.distributedpaymentsystem.api.merchant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;

public class CreateMerchantResponse {

    private UUID merchantId;
    private String merchantName;
    private String email;
    private String webhookUrl;
    private String phone;
    private MerchantStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public CreateMerchantResponse() {
    }

    public CreateMerchantResponse(UUID merchantId, String merchantName, String email, String webhookUrl,
            String phone, MerchantStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.email = email;
        this.webhookUrl = webhookUrl;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public MerchantStatus getStatus() { return status; }
    public void setStatus(MerchantStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
