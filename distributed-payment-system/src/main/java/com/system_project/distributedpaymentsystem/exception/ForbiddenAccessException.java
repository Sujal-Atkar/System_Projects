package com.system_project.distributedpaymentsystem.exception;

import java.util.UUID;

public class ForbiddenAccessException extends ApplicationException {

    public ForbiddenAccessException(UUID merchantId) {
        super("FORBIDDEN", "Access denied for merchant: " + merchantId);
    }

    public ForbiddenAccessException(String message) {
        super("FORBIDDEN", message);
    }
}
