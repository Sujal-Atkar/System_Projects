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
@Table(name = "reconciliation_attempt")
public class ReconciliationAttempt {

    @Id
    @Column(name = "reconciliation_attempt_id", nullable = false)
    private UUID reconciliationAttemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private ReconciliationCase reconciliationCase;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "response_at")
    private OffsetDateTime responseAt;

    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "result", columnDefinition = "reconciliation_attempt_result", nullable = false)
    private ReconciliationAttemptResult result;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected ReconciliationAttempt() {
    }

    public ReconciliationAttempt(UUID reconciliationAttemptId, ReconciliationCase reconciliationCase,
            Integer attemptNumber, OffsetDateTime requestedAt, OffsetDateTime responseAt,
            String providerStatus, ReconciliationAttemptResult result, String errorMessage) {
        this.reconciliationAttemptId = reconciliationAttemptId;
        this.reconciliationCase = reconciliationCase;
        this.attemptNumber = attemptNumber;
        this.requestedAt = requestedAt;
        this.responseAt = responseAt;
        this.providerStatus = providerStatus;
        this.result = result;
        this.errorMessage = errorMessage;
    }

    public UUID getReconciliationAttemptId() {
        return reconciliationAttemptId;
    }

    public void setReconciliationAttemptId(UUID reconciliationAttemptId) {
        this.reconciliationAttemptId = reconciliationAttemptId;
    }

    public ReconciliationCase getReconciliationCase() {
        return reconciliationCase;
    }

    public void setReconciliationCase(ReconciliationCase reconciliationCase) {
        this.reconciliationCase = reconciliationCase;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getResponseAt() {
        return responseAt;
    }

    public void setResponseAt(OffsetDateTime responseAt) {
        this.responseAt = responseAt;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public ReconciliationAttemptResult getResult() {
        return result;
    }

    public void setResult(ReconciliationAttemptResult result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
