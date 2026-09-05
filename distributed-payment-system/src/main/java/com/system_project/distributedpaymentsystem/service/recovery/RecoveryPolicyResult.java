package com.system_project.distributedpaymentsystem.service.recovery;

public class RecoveryPolicyResult {

    private final RecoveryPolicyReason reason;
    private final RecoveryActionType recommendedAction;
    private final boolean retryAllowed;
    private final boolean rerouteAllowed;
    private final boolean blocked;

    public RecoveryPolicyResult(RecoveryPolicyReason reason, RecoveryActionType recommendedAction,
            boolean retryAllowed, boolean rerouteAllowed, boolean blocked) {
        this.reason = reason;
        this.recommendedAction = recommendedAction;
        this.retryAllowed = retryAllowed;
        this.rerouteAllowed = rerouteAllowed;
        this.blocked = blocked;
    }

    public RecoveryPolicyReason getReason() {
        return reason;
    }

    public RecoveryActionType getRecommendedAction() {
        return recommendedAction;
    }

    public boolean isRetryAllowed() {
        return retryAllowed;
    }

    public boolean isRerouteAllowed() {
        return rerouteAllowed;
    }

    public boolean isBlocked() {
        return blocked;
    }
}
