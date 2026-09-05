package com.system_project.distributedpaymentsystem.exception;

import java.util.UUID;

public class DuplicateIdempotencyKeyException extends ApplicationException {

    public DuplicateIdempotencyKeyException(UUID merchantId, String idempotencyKey) {
        super("DUPLICATE_IDEMPOTENCY_KEY",
                "Duplicate idempotency key for merchant " + merchantId + ": " + idempotencyKey);
    }
}
