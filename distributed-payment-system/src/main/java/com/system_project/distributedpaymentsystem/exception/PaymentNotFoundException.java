package com.system_project.distributedpaymentsystem.exception;

import java.util.UUID;

public class PaymentNotFoundException extends ApplicationException {

    public PaymentNotFoundException(UUID paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment not found: " + paymentId);
    }
}
