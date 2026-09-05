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
@Table(name = "revenue_risk")
public class RevenueRisk {

    @Id
    @Column(name = "risk_id", nullable = false)
    private UUID riskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "risk_type", columnDefinition = "risk_type", nullable = false)
    private RiskType riskType;

    @Column(name = "risk_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal riskAmount;

    @Column(name = "currency", columnDefinition = "bpchar", length = 3)
    private String currency;

    @Column(name = "risk_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal riskScore;

    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "revenue_risk_status", nullable = false)
    private RevenueRiskStatus status;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected RevenueRisk() {
    }

    public RevenueRisk(UUID riskId, Merchant merchant, Payment payment, RiskType riskType,
            BigDecimal riskAmount, String currency, BigDecimal riskScore, String reason,
            RevenueRiskStatus status, OffsetDateTime detectedAt, OffsetDateTime resolvedAt) {
        this.riskId = riskId;
        this.merchant = merchant;
        this.payment = payment;
        this.riskType = riskType;
        this.riskAmount = riskAmount;
        this.currency = currency;
        this.riskScore = riskScore;
        this.reason = reason;
        this.status = status;
        this.detectedAt = detectedAt;
        this.resolvedAt = resolvedAt;
    }

    public UUID getRiskId() { return riskId; }
    public void setRiskId(UUID riskId) { this.riskId = riskId; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public RiskType getRiskType() { return riskType; }
    public void setRiskType(RiskType riskType) { this.riskType = riskType; }
    public BigDecimal getRiskAmount() { return riskAmount; }
    public void setRiskAmount(BigDecimal riskAmount) { this.riskAmount = riskAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RevenueRiskStatus getStatus() { return status; }
    public void setStatus(RevenueRiskStatus status) { this.status = status; }
    public OffsetDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(OffsetDateTime detectedAt) { this.detectedAt = detectedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
