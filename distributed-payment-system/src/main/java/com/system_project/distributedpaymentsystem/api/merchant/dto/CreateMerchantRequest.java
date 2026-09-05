package com.system_project.distributedpaymentsystem.api.merchant.dto;

import com.system_project.distributedpaymentsystem.domain.entity.Merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateMerchantRequest {

    @NotBlank(message = "merchantName is required")
    @Size(max = 150, message = "merchantName must be at most 150 characters")
    private String merchantName;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Size(max = 120, message = "email must be at most 120 characters")
    private String email;

    @Size(max = 2048, message = "webhookUrl must be at most 2048 characters")
    private String webhookUrl;

    @Size(max = 20, message = "phone must be at most 20 characters")
    private String phone;

    public CreateMerchantRequest() {
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
