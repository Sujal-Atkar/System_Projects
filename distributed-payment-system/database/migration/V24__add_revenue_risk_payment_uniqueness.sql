-- V24: Add partial unique index on payment_id for active RevenueRisk records
-- This ensures only one active RevenueRisk per payment, preventing duplicates during Kafka redelivery

CREATE UNIQUE INDEX idx_revenue_risk_payment_id_active
    ON revenue_risk(payment_id)
    WHERE status IN ('DETECTED', 'IN_RECOVERY');
