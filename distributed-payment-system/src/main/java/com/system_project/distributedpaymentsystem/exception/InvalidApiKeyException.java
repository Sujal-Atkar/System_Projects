package com.system_project.distributedpaymentsystem.exception;

public class InvalidApiKeyException extends ApplicationException {

    public InvalidApiKeyException() {
        super("INVALID_API_KEY", "Invalid or expired API key");
    }

    public InvalidApiKeyException(String message) {
        super("INVALID_API_KEY", message);
    }
}
