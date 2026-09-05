package com.system_project.distributedpaymentsystem.domain.entity;

import java.math.BigDecimal;
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
@Table(name = "recovery_result")
public class RecoveryResult {

    @Id
    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false, unique = true)
    private RecoveryAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "outcome", columnDefinition = "recovery_result_outcome", nullable = false)
    private RecoveryResultOutcome outcome;

    @Column(name = "recovered_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal recoveredAmount;

    @Column(name = "currency", columnDefinition = "bpchar", length = 3, nullable = false)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_payment_id")
    private Payment resultingPayment;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    protected RecoveryResult() {
    }

    public RecoveryResult(UUID resultId, RecoveryAction action, RecoveryResultOutcome outcome,
            BigDecimal recoveredAmount, String currency, Payment resultingPayment, OffsetDateTime recordedAt) {
        this.resultId = resultId;
        this.action = action;
        this.outcome = outcome;
        this.recoveredAmount = recoveredAmount;
        this.currency = currency;
        this.resultingPayment = resultingPayment;
        this.recordedAt = recordedAt;
    }

    public UUID getResultId() { return resultId; }
    public void setResultId(UUID resultId) { this.resultId = resultId; }
    public RecoveryAction getAction() { return action; }
    public void setAction(RecoveryAction action) { this.action = action; }
    public RecoveryResultOutcome getOutcome() { return outcome; }
    public void setOutcome(RecoveryResultOutcome outcome) { this.outcome = outcome; }
    public BigDecimal getRecoveredAmount() { return recoveredAmount; }
    public void setRecoveredAmount(BigDecimal recoveredAmount) { this.recoveredAmount = recoveredAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Payment getResultingPayment() { return resultingPayment; }
    public void setResultingPayment(Payment resultingPayment) { this.resultingPayment = resultingPayment; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
