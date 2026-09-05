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
@Table(name = "ledger_transaction")
public class LedgerTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "transaction_type", columnDefinition = "ledger_transaction_type", nullable = false)
    private LedgerTransactionType transactionType;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "reference_id", length = 100, nullable = false)
    private String referenceId;

    @Column(name = "currency", columnDefinition = "bpchar", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "ledger_transaction_status", nullable = false)
    private LedgerTransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(UUID transactionId, Payment payment, LedgerTransactionType transactionType,
            BigDecimal amount, String referenceId, String currency, LedgerTransactionStatus status,
            OffsetDateTime createdAt) {
        this.transactionId = transactionId;
        this.payment = payment;
        this.transactionType = transactionType;
        this.amount = amount;
        this.referenceId = referenceId;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public LedgerTransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(LedgerTransactionType transactionType) { this.transactionType = transactionType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LedgerTransactionStatus getStatus() { return status; }
    public void setStatus(LedgerTransactionStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
