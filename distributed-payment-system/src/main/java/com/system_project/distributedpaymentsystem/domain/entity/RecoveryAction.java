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

import com.system_project.distributedpaymentsystem.service.recovery.RecoveryActionType;

@Entity
@Table(name = "recovery_action")
public class RecoveryAction {

    @Id
    @Column(name = "action_id", nullable = false)
    private UUID actionId;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "risk_id", nullable = false)
    private UUID riskId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "action_type", columnDefinition = "recovery_action_type", nullable = false)
    private RecoveryActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_provider_id")
    private Provider targetProvider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "recovery_action_status", nullable = false)
    private RecoveryActionStatus status;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    protected RecoveryAction() {
    }

    public RecoveryAction(UUID actionId, UUID decisionId, Payment payment, UUID riskId,
            RecoveryActionType actionType, Provider targetProvider, RecoveryActionStatus status,
            Integer attemptNumber, OffsetDateTime executedAt, OffsetDateTime completedAt,
            String failureReason) {
        this.actionId = actionId;
        this.decisionId = decisionId;
        this.payment = payment;
        this.riskId = riskId;
        this.actionType = actionType;
        this.targetProvider = targetProvider;
        this.status = status;
        this.attemptNumber = attemptNumber;
        this.executedAt = executedAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
    }

    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID decisionId) { this.decisionId = decisionId; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public UUID getRiskId() { return riskId; }
    public void setRiskId(UUID riskId) { this.riskId = riskId; }
    public RecoveryActionType getActionType() { return actionType; }
    public void setActionType(RecoveryActionType actionType) { this.actionType = actionType; }
    public Provider getTargetProvider() { return targetProvider; }
    public void setTargetProvider(Provider targetProvider) { this.targetProvider = targetProvider; }
    public RecoveryActionStatus getStatus() { return status; }
    public void setStatus(RecoveryActionStatus status) { this.status = status; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
