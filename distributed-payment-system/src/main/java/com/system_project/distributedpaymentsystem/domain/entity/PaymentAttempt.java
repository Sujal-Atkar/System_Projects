package com.system_project.distributedpaymentsystem.domain.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", columnDefinition = "bpchar", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "payment_attempt_status", nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "provider_reference", length = 120, unique = true)
    private String providerReference;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private Map<String, Object> requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID attemptId, Payment payment, Provider provider, Integer attemptNumber,
            BigDecimal amount, String currency, PaymentAttemptStatus status, String providerReference,
            String errorCode, String failureReason, Map<String, Object> requestPayload,
            Map<String, Object> responsePayload, Integer latencyMs, OffsetDateTime requestedAt,
            OffsetDateTime respondedAt) {
        this.attemptId = attemptId;
        this.payment = payment;
        this.provider = provider;
        this.attemptNumber = attemptNumber;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.providerReference = providerReference;
        this.errorCode = errorCode;
        this.failureReason = failureReason;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.latencyMs = latencyMs;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
    }

    public UUID getAttemptId() { return attemptId; }
    public void setAttemptId(UUID attemptId) { this.attemptId = attemptId; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentAttemptStatus getStatus() { return status; }
    public void setStatus(PaymentAttemptStatus status) { this.status = status; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Map<String, Object> getRequestPayload() { return requestPayload; }
    public void setRequestPayload(Map<String, Object> requestPayload) { this.requestPayload = requestPayload; }
    public Map<String, Object> getResponsePayload() { return responsePayload; }
    public void setResponsePayload(Map<String, Object> responsePayload) { this.responsePayload = responsePayload; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime respondedAt) { this.respondedAt = respondedAt; }
}