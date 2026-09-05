package com.system_project.distributedpaymentsystem.provider;

public class ProviderProcessingResult {

    public enum ProviderOutcome {
        SUCCESS,
        FAILED,
        TIMEOUT
    }

    private final ProviderOutcome outcome;
    private final String providerReference;
    private final String errorCode;
    private final String failureReason;

    public ProviderProcessingResult(boolean success, String providerReference, String errorCode, String failureReason) {
        this(success ? ProviderOutcome.SUCCESS : ProviderOutcome.FAILED, providerReference, errorCode, failureReason);
    }

    public ProviderProcessingResult(ProviderOutcome outcome, String providerReference, String errorCode, String failureReason) {
        this.outcome = outcome;
        this.providerReference = providerReference;
        this.errorCode = errorCode;
        this.failureReason = failureReason;
    }

    public static ProviderProcessingResult success(String providerReference, String errorCode, String failureReason) {
        return new ProviderProcessingResult(ProviderOutcome.SUCCESS, providerReference, errorCode, failureReason);
    }

    public static ProviderProcessingResult failed(String providerReference, String errorCode, String failureReason) {
        return new ProviderProcessingResult(ProviderOutcome.FAILED, providerReference, errorCode, failureReason);
    }

    public static ProviderProcessingResult timeout(String providerReference, String errorCode, String failureReason) {
        return new ProviderProcessingResult(ProviderOutcome.TIMEOUT, providerReference, errorCode, failureReason);
    }

    public boolean isSuccess() {
        return outcome == ProviderOutcome.SUCCESS;
    }

    public boolean isFailed() {
        return outcome == ProviderOutcome.FAILED;
    }

    public boolean isTimeout() {
        return outcome == ProviderOutcome.TIMEOUT;
    }

    public ProviderOutcome getOutcome() {
        return outcome;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
