package com.system_project.distributedpaymentsystem.api.internal.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class RecoveryExecuteResponse {
    private boolean success;
    private String providerUsed;
    private String outcomeCode;
    private BigDecimal recoveredAmount;
    private OffsetDateTime executedAt;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getProviderUsed() { return providerUsed; }
    public void setProviderUsed(String providerUsed) { this.providerUsed = providerUsed; }
    public String getOutcomeCode() { return outcomeCode; }
    public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }
    public BigDecimal getRecoveredAmount() { return recoveredAmount; }
    public void setRecoveredAmount(BigDecimal recoveredAmount) { this.recoveredAmount = recoveredAmount; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
}
