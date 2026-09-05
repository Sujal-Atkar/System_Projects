package com.system_project.distributedpaymentsystem.api.internal.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class RecoveryContextResponse {
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;
    private String paymentStatus;
    private Integer attemptNumber;
    private Integer previousAttempts;
    private String provider;
    private String providerHealth;
    private String failureType;
    private String failureReason;
    private boolean reconciliationOpen;
    private OffsetDateTime lastAttemptAt;

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Integer getPreviousAttempts() { return previousAttempts; }
    public void setPreviousAttempts(Integer previousAttempts) { this.previousAttempts = previousAttempts; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderHealth() { return providerHealth; }
    public void setProviderHealth(String providerHealth) { this.providerHealth = providerHealth; }
    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public boolean isReconciliationOpen() { return reconciliationOpen; }
    public void setReconciliationOpen(boolean reconciliationOpen) { this.reconciliationOpen = reconciliationOpen; }
    public OffsetDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(OffsetDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
}
