package com.system_project.distributedpaymentsystem.exception;

import java.util.UUID;

public class MerchantNotFoundException extends ApplicationException {

    public MerchantNotFoundException(UUID merchantId) {
        super("MERCHANT_NOT_FOUND", "Merchant not found: " + merchantId);
    }
}
