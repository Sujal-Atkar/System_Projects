package com.system_project.distributedpaymentsystem.exception;

public class NoEligibleProviderException extends ApplicationException {

    public NoEligibleProviderException(String message) {
        super("NO_ELIGIBLE_PROVIDER", message);
    }
}
