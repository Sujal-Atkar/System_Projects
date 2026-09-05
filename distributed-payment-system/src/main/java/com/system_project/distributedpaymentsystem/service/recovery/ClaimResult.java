package com.system_project.distributedpaymentsystem.service.recovery;

public class ClaimResult {

    private final boolean claimed;
    private final String reason;

    private ClaimResult(boolean claimed, String reason) {
        this.claimed = claimed;
        this.reason = reason;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public String getReason() {
        return reason;
    }

    public static ClaimResult success() {
        return new ClaimResult(true, null);
    }

    public static ClaimResult failure(String reason) {
        return new ClaimResult(false, reason);
    }
}
