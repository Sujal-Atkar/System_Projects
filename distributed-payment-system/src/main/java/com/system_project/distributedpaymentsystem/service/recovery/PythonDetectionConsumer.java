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

@Component
public class PythonDetectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(PythonDetectionConsumer.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final PythonDetectionWebhookClient webhookClient;
    private final ObjectMapper objectMapper;

    public PythonDetectionConsumer(PythonDetectionWebhookClient webhookClient, ObjectMapper objectMapper) {
        this.webhookClient = webhookClient;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${payment.outbox.topic:payment-events}",
            groupId = "${payment.recovery.consumer.python-group-id:python-detection-consumer-group}")
    public void consume(ConsumerRecord<String, String> record) {
        processPaymentEvent(record.key(), record.value());
    }

    public void processPaymentEvent(String eventKey, String payloadJson) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (Exception ex) {
            log.warn("Ignoring malformed payment event payload", ex);
            return;
        }

        if (isNonRecoveryEvent(payload)) {
            return;
        }

        String paymentIdText = valueAsString(payload.get("paymentId"));
        if (paymentIdText == null || paymentIdText.isBlank()) {
            log.warn("Ignoring invalid recovery payment event: paymentId is missing");
            return;
        }

        // Notify the Python system asynchronously
        log.info("Payment failure detected. Notifying Python AI for payment: {}", paymentIdText);
        webhookClient.notifyPythonSystem(paymentIdText, payload);
    }

    private boolean isNonRecoveryEvent(Map<String, Object> payload) {
        String eventType = valueAsString(payload.get("eventType"));
        if (eventType != null && !"PAYMENT_FAILED".equalsIgnoreCase(eventType)) {
            return true;
        }

        String status = valueAsString(payload.get("status"));
        return status == null || !"FAILED".equalsIgnoreCase(status);
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }
}
