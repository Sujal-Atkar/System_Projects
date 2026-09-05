package com.system_project.distributedpaymentsystem.domain.entity;

import java.time.OffsetDateTime;
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
@Table(name = "reconciliation_case")
public class ReconciliationCase {

    @Id
    @Column(name = "reconciliation_id", nullable = false)
    private UUID reconciliationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private PaymentAttempt attempt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "reconciliation_case_status", nullable = false)
    private ReconciliationCaseStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reason", columnDefinition = "reconciliation_reason", nullable = false)
    private ReconciliationReason reason;

    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "resolved_status", columnDefinition = "reconciliation_resolved_status")
    private ReconciliationResolvedStatus resolvedStatus;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_check_at")
    private OffsetDateTime nextCheckAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected ReconciliationCase() {
    }

    public ReconciliationCase(UUID reconciliationId, Payment payment, PaymentAttempt attempt,
            ReconciliationCaseStatus status, ReconciliationReason reason, String providerStatus,
            ReconciliationResolvedStatus resolvedStatus, Integer retryCount,
            OffsetDateTime nextCheckAt, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        this.reconciliationId = reconciliationId;
        this.payment = payment;
        this.attempt = attempt;
        this.status = status;
        this.reason = reason;
        this.providerStatus = providerStatus;
        this.resolvedStatus = resolvedStatus;
        this.retryCount = retryCount;
        this.nextCheckAt = nextCheckAt;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public UUID getReconciliationId() {
        return reconciliationId;
    }

    public void setReconciliationId(UUID reconciliationId) {
        this.reconciliationId = reconciliationId;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public PaymentAttempt getAttempt() {
        return attempt;
    }

    public void setAttempt(PaymentAttempt attempt) {
        this.attempt = attempt;
    }

    public ReconciliationCaseStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationCaseStatus status) {
        this.status = status;
    }

    public ReconciliationReason getReason() {
        return reason;
    }

    public void setReason(ReconciliationReason reason) {
        this.reason = reason;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public ReconciliationResolvedStatus getResolvedStatus() {
        return resolvedStatus;
    }

    public void setResolvedStatus(ReconciliationResolvedStatus resolvedStatus) {
        this.resolvedStatus = resolvedStatus;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public OffsetDateTime getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(OffsetDateTime nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
