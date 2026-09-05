package com.system_project.distributedpaymentsystem.service.recovery;

public enum RecoveryPolicyReason {
    BUSINESS_FAILURE,
    TECHNICAL_TRANSIENT_FAILURE,
    RECONCILIATION_OPEN,
    MAX_ATTEMPTS_REACHED,
    PROVIDER_UNAVAILABLE,
    UNKNOWN
}
