package com.system_project.distributedpaymentsystem.api.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.system_project.distributedpaymentsystem.domain.entity.ApiCredential;
import com.system_project.distributedpaymentsystem.exception.ForbiddenAccessException;
import com.system_project.distributedpaymentsystem.exception.InvalidApiKeyException;
import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(1)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_API_SECRET = "X-API-Secret";
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    private final ApiCredentialService apiCredentialService;

    public ApiKeyAuthenticationFilter(ApiCredentialService apiCredentialService) {
        this.apiCredentialService = apiCredentialService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isMerchantRegistration = "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/merchants".equals(path);
        return isMerchantRegistration || path.startsWith("/actuator") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(HEADER_API_KEY);
        String apiSecret = request.getHeader(HEADER_API_SECRET);

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key or X-API-Secret header");
            return;
        }

        try {
            ApiCredential credential = apiCredentialService.authenticate(apiKey, apiSecret);
            UUID authenticatedMerchantId = credential.getMerchant().getMerchantId();
            request.setAttribute("authenticatedMerchantId", authenticatedMerchantId);
            String clientMerchantIdHeader = request.getHeader(HEADER_MERCHANT_ID);
            if (clientMerchantIdHeader != null && !clientMerchantIdHeader.isBlank()) {
                UUID clientMerchantId = UUID.fromString(clientMerchantIdHeader);
                if (!authenticatedMerchantId.equals(clientMerchantId)) {
                    throw new ForbiddenAccessException("X-Merchant-Id does not match authenticated API credential merchant");
                }
            }
            filterChain.doFilter(request, response);
        } catch (InvalidApiKeyException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Merchant-Id header");
        } catch (ForbiddenAccessException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        }
    }
}
