package com.system_project.distributedpaymentsystem.service.recovery;

import java.math.BigDecimal;

public class GeminiDecisionResult {

    private final String decision;
    private final String reasoning;
    private final BigDecimal confidence;
    private final boolean isValid;
    private final String errorMessage;

    private GeminiDecisionResult(String decision, String reasoning, BigDecimal confidence, boolean isValid, String errorMessage) {
        this.decision = decision;
        this.reasoning = reasoning;
        this.confidence = confidence;
        this.isValid = isValid;
        this.errorMessage = errorMessage;
    }

    public String getDecision() { return decision; }
    public String getReasoning() { return reasoning; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isValid() { return isValid; }
    public String getErrorMessage() { return errorMessage; }

    public static GeminiDecisionResult success(String decision, String reasoning, BigDecimal confidence) {
        return new GeminiDecisionResult(decision, reasoning, confidence, true, null);
    }

    public static GeminiDecisionResult failure(String errorMessage) {
        return new GeminiDecisionResult(null, null, null, false, errorMessage);
    }
}
