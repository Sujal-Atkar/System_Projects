package com.system_project.distributedpaymentsystem.service.recovery;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;

@Component
public class RecoveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecoveryConsumer.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final RecoveryService recoveryService;
    private final ObjectMapper objectMapper;

    public RecoveryConsumer(RecoveryService recoveryService, ObjectMapper objectMapper) {
        this.recoveryService = recoveryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${payment.outbox.topic:payment-events}",
            groupId = "${payment.recovery.consumer.group-id:recovery-consumer-group}")
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

        if (isNonRecoveryEvent(payload)) {
            return false;
        }

        UUID paymentId;
        try {
            paymentId = parsePaymentId(eventKey, payload);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring invalid recovery payment event: {}", ex.getMessage());
            return false;
        }

        RecoveryAction action = recoveryService.processFailedPayment(paymentId);
        return action != null;
    }

    private boolean isNonRecoveryEvent(Map<String, Object> payload) {
        String eventType = valueAsString(payload.get("eventType"));
        if (eventType != null && !"PAYMENT_FAILED".equalsIgnoreCase(eventType)) {
            return true;
        }

        String status = valueAsString(payload.get("status"));
        return status == null || !"FAILED".equalsIgnoreCase(status);
    }

    private UUID parsePaymentId(String eventKey, Map<String, Object> payload) {
        String paymentIdText = valueAsString(payload.get("paymentId"));
        if (paymentIdText == null || paymentIdText.isBlank()) {
            throw new IllegalArgumentException("paymentId is required");
        }

        try {
            UUID paymentId = UUID.fromString(paymentIdText);
            if (eventKey != null && !eventKey.isBlank() && !eventKey.equals(paymentId.toString())) {
                throw new IllegalArgumentException("event key does not match paymentId");
            }
            return paymentId;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("paymentId is invalid");
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }
}
