package com.system_project.distributedpaymentsystem.service.reconciliation;

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
public class ReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationConsumer.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    public ReconciliationConsumer(ReconciliationService reconciliationService, ObjectMapper objectMapper) {
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${payment.outbox.topic:payment-events}",
            groupId = "${payment.reconciliation.consumer.group-id:reconciliation-consumer-group}")
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

        if (isNonUnknownPaymentEvent(payload)) {
            return false;
        }

        UUID paymentId;
        try {
            paymentId = parsePaymentId(eventKey, payload);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring invalid reconciliation payment event: {}", ex.getMessage());
            return false;
        }

        try {
            reconciliationService.reconcilePayment(paymentId);
            return true;
        } catch (IllegalStateException ex) {
            if (isAlreadyResolvedPayment(ex)) {
                log.info("Ignoring late PAYMENT_UNKNOWN event for already resolved payment {}", paymentId);
                return false;
            }
            throw ex;
        }
    }

    private boolean isNonUnknownPaymentEvent(Map<String, Object> payload) {
        String eventType = valueAsString(payload.get("eventType"));
        if (eventType != null && !"PAYMENT_UNKNOWN".equalsIgnoreCase(eventType)) {
            return true;
        }

        String status = valueAsString(payload.get("status"));
        return status == null || !"UNKNOWN".equalsIgnoreCase(status);
    }

    private UUID parsePaymentId(String eventKey, Map<String, Object> payload) {
        String paymentIdText = valueAsString(payload.get("paymentId"));
        if (paymentIdText == null || paymentIdText.isBlank()) {
            throw new IllegalArgumentException("paymentId is required");
        }

        UUID paymentId;
        try {
            paymentId = UUID.fromString(paymentIdText);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("paymentId is invalid");
        }

        if (eventKey != null && !eventKey.isBlank() && !eventKey.equals(paymentId.toString())) {
            throw new IllegalArgumentException("event key does not match paymentId");
        }

        return paymentId;
    }

    private boolean isAlreadyResolvedPayment(IllegalStateException ex) {
        return ex.getMessage() != null && ex.getMessage().startsWith("Payment is not UNKNOWN");
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }
}
