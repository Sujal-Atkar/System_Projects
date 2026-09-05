package com.system_project.distributedpaymentsystem.service.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentLedgerEvent {

    private final UUID paymentId;
    private final UUID merchantId;
    private final BigDecimal amount;
    private final String currency;
    private final String status;

    public PaymentLedgerEvent(UUID paymentId, UUID merchantId, BigDecimal amount, String currency, String status) {
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    public UUID getPaymentId() { return paymentId; }
    public UUID getMerchantId() { return merchantId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
}
