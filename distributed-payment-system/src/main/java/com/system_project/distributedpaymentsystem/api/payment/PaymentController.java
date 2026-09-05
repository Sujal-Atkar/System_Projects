package com.system_project.distributedpaymentsystem.api.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.system_project.distributedpaymentsystem.api.auth.AuthenticatedMerchantContext;
import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentRequest;
import com.system_project.distributedpaymentsystem.api.payment.dto.CreatePaymentResponse;
import com.system_project.distributedpaymentsystem.api.payment.dto.GetPaymentResponse;
import com.system_project.distributedpaymentsystem.exception.ForbiddenAccessException;
import com.system_project.distributedpaymentsystem.service.payment.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse createPayment(
            @RequestHeader(name = "X-Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpServletRequest) {
        UUID merchantId = AuthenticatedMerchantContext.getAuthenticatedMerchantId(httpServletRequest);
        String clientMerchantId = httpServletRequest.getHeader("X-Merchant-Id");
        if (clientMerchantId != null && !clientMerchantId.isBlank() && !UUID.fromString(clientMerchantId).equals(merchantId)) {
            throw new ForbiddenAccessException("X-Merchant-Id does not match authenticated API credential merchant");
        }
        return paymentService.createPayment(merchantId, request, idempotencyKey);
    }

    @GetMapping("/payments/{paymentId}")
    public GetPaymentResponse getPayment(@PathVariable UUID paymentId, HttpServletRequest httpServletRequest) {
        UUID authenticatedMerchantId = AuthenticatedMerchantContext.getAuthenticatedMerchantId(httpServletRequest);
        GetPaymentResponse payment = paymentService.getPayment(paymentId);
        if (!payment.getMerchantId().equals(authenticatedMerchantId)) {
            throw new ForbiddenAccessException(authenticatedMerchantId);
        }
        return payment;
    }
}
