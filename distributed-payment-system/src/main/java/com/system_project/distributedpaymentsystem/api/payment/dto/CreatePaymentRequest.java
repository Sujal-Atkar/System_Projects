package com.system_project.distributedpaymentsystem.api.payment.dto;

import java.math.BigDecimal;

import com.system_project.distributedpaymentsystem.domain.entity.PaymentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreatePaymentRequest {

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be exactly 3 characters")
    private String currency;

    @NotBlank(message = "customerRef is required")
    @Size(max = 120, message = "customerRef must be at most 120 characters")
    private String customerRef;

    @NotBlank(message = "orderRef is required")
    @Size(max = 120, message = "orderRef must be at most 120 characters")
    private String orderRef;

    @NotNull(message = "paymentType is required")
    private PaymentType paymentType;

    @Size(max = 1000, message = "description must be at most 1000 characters")
    private String description;

    public CreatePaymentRequest() {
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public void setCustomerRef(String customerRef) {
        this.customerRef = customerRef;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}