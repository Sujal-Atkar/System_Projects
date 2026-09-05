package com.system_project.distributedpaymentsystem.domain.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_id", nullable = false)
    private UUID idempotencyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "idempotency_key", length = 100, nullable = false)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(name = "request_hash", length = 128, nullable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "idempotency_record_status", nullable = false)
    private IdempotencyRecordStatus status;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID idempotencyId, Merchant merchant, String idempotencyKey, Payment payment,
            String requestHash, Integer responseStatus, OffsetDateTime createdAt, OffsetDateTime expiresAt,
            Map<String, Object> responsePayload, IdempotencyRecordStatus status) {
        this.idempotencyId = idempotencyId;
        this.merchant = merchant;
        this.idempotencyKey = idempotencyKey;
        this.payment = payment;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.responsePayload = responsePayload;
        this.status = status;
    }

    public UUID getIdempotencyId() {
        return idempotencyId;
    }

    public void setIdempotencyId(UUID idempotencyId) {
        this.idempotencyId = idempotencyId;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Map<String, Object> getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(Map<String, Object> responsePayload) {
        this.responsePayload = responsePayload;
    }

    public IdempotencyRecordStatus getStatus() {
        return status;
    }

    public void setStatus(IdempotencyRecordStatus status) {
        this.status = status;
    }
}