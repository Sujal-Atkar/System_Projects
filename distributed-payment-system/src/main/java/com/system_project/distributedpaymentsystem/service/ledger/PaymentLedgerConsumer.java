package com.system_project.distributedpaymentsystem.service.ledger;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentLedgerConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentLedgerConsumer.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public PaymentLedgerConsumer(LedgerService ledgerService, ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${payment.outbox.topic:payment-events}",
            groupId = "${payment.ledger.consumer.group-id:ledger-consumer-group}")
    public void consume(ConsumerRecord<String, String> record) {
        processPaymentEvent(record.key(), record.value());
    }

    public boolean processPaymentEvent(String eventKey, String payloadJson) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (Exception ex) {
            log.warn("Ignoring malformed payment event payload", ex);
            return false;
        }

        if (isNonSuccessfulPaymentEvent(payload)) {
            return false;
        }

        PaymentLedgerEvent event;
        try {
            event = toPaymentLedgerEvent(eventKey, payload);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring invalid payment event payload: {}", ex.getMessage());
            return false;
        }

        ledgerService.postSuccessfulPayment(event);
        return true;
    }

    private boolean isNonSuccessfulPaymentEvent(Map<String, Object> payload) {
        String eventType = valueAsString(payload.get("eventType"));
        if (eventType != null && !"PAYMENT_SUCCEEDED".equalsIgnoreCase(eventType)) {
            return true;
        }

        String status = valueAsString(payload.get("status"));
        return status == null || !"SUCCESS".equalsIgnoreCase(status);
    }

    private PaymentLedgerEvent toPaymentLedgerEvent(String eventKey, Map<String, Object> payload) {
        UUID paymentId = parseUuid(payload.get("paymentId"), "paymentId");
        if (eventKey != null && !eventKey.isBlank() && !eventKey.equals(paymentId.toString())) {
            throw new IllegalArgumentException("event key does not match paymentId");
        }

        UUID merchantId = parseUuid(payload.get("merchantId"), "merchantId");
        BigDecimal amount = parseAmount(payload.get("amount"));
        String currency = valueAsString(payload.get("currency"));
        String status = valueAsString(payload.get("status"));

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        return new PaymentLedgerEvent(paymentId, merchantId, amount, currency.trim().toUpperCase(), status);
    }

    private UUID parseUuid(Object value, String fieldName) {
        String text = valueAsString(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        try {
            BigDecimal amount = new BigDecimal(value.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("amount is invalid");
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }
}
