package com.system_project.distributedpaymentsystem.service.recovery;

import java.math.BigDecimal;
import java.util.List;

public class AIRecoveryContext {

    private BigDecimal paymentAmount;
    private String currency;
    private String failureReason;
    private String failureType;
    private String providerCode;
    private int attemptNumber;
    private List<String> allowedActions;
    private BigDecimal riskScore;

    public AIRecoveryContext(BigDecimal paymentAmount, String currency, String failureReason,
            String failureType, String providerCode, int attemptNumber,
            List<String> allowedActions, BigDecimal riskScore) {
        this.paymentAmount = paymentAmount;
        this.currency = currency;
        this.failureReason = failureReason;
        this.failureType = failureType;
        this.providerCode = providerCode;
        this.attemptNumber = attemptNumber;
        this.allowedActions = allowedActions;
        this.riskScore = riskScore;
    }

    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public String getCurrency() { return currency; }
    public String getFailureReason() { return failureReason; }
    public String getFailureType() { return failureType; }
    public String getProviderCode() { return providerCode; }
    public int getAttemptNumber() { return attemptNumber; }
    public List<String> getAllowedActions() { return allowedActions; }
    public BigDecimal getRiskScore() { return riskScore; }
}
