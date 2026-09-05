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
@Table(name = "recovery_decision")
public class RecoveryDecision {

    @Id
    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_id", nullable = false)
    private RevenueRisk risk;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "decision_type", columnDefinition = "decision_type", nullable = false)
    private DecisionType decisionType;

    @Column(name = "reasoning", columnDefinition = "text", nullable = false)
    private String reasoning;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "bounded", nullable = false)
    private Boolean bounded;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RecoveryDecision() {
    }

    public RecoveryDecision(UUID decisionId, RevenueRisk risk, DecisionType decisionType,
            String reasoning, BigDecimal confidenceScore, String modelVersion,
            Boolean bounded, OffsetDateTime createdAt) {
        this.decisionId = decisionId;
        this.risk = risk;
        this.decisionType = decisionType;
        this.reasoning = reasoning;
        this.confidenceScore = confidenceScore;
        this.modelVersion = modelVersion;
        this.bounded = bounded;
        this.createdAt = createdAt;
    }

    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID decisionId) { this.decisionId = decisionId; }
    public RevenueRisk getRisk() { return risk; }
    public void setRisk(RevenueRisk risk) { this.risk = risk; }
    public DecisionType getDecisionType() { return decisionType; }
    public void setDecisionType(DecisionType decisionType) { this.decisionType = decisionType; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public Boolean getBounded() { return bounded; }
    public void setBounded(Boolean bounded) { this.bounded = bounded; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
