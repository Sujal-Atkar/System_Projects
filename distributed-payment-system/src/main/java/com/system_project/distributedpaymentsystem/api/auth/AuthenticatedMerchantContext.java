package com.system_project.distributedpaymentsystem.api.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthenticatedMerchantContext {

    private AuthenticatedMerchantContext() {
    }

    public static UUID getAuthenticatedMerchantId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedMerchantId");
        if (attribute instanceof UUID merchantId) {
            return merchantId;
        }
        throw new IllegalStateException("No authenticated merchant found on request");
    }
}
