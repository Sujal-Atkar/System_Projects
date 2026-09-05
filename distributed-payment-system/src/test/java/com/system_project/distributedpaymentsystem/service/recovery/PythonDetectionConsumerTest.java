package com.system_project.distributedpaymentsystem.service.recovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class PythonDetectionConsumerTest {

    @Mock
    private PythonDetectionWebhookClient webhookClient;

    private ObjectMapper objectMapper;
    private PythonDetectionConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new PythonDetectionConsumer(webhookClient, objectMapper);
    }

    @Test
    void processPaymentEvent_failedEvent_triggersWebhook() throws Exception {
        String payloadJson = "{\"eventType\":\"PAYMENT_FAILED\", \"status\":\"FAILED\", \"paymentId\":\"123e4567-e89b-12d3-a456-426614174000\"}";

        consumer.processPaymentEvent("123e4567-e89b-12d3-a456-426614174000", payloadJson);

        verify(webhookClient).notifyPythonSystem(eq("123e4567-e89b-12d3-a456-426614174000"), any(Map.class));
    }

    @Test
    void processPaymentEvent_successEvent_isIgnored() throws Exception {
        String payloadJson = "{\"eventType\":\"PAYMENT_SUCCESS\", \"status\":\"SUCCESS\", \"paymentId\":\"123e4567-e89b-12d3-a456-426614174000\"}";

        consumer.processPaymentEvent("123e4567-e89b-12d3-a456-426614174000", payloadJson);

        verify(webhookClient, never()).notifyPythonSystem(anyString(), any(Map.class));
    }

    @Test
    void processPaymentEvent_malformedJson_isIgnored() {
        String payloadJson = "invalid-json";

        consumer.processPaymentEvent("123", payloadJson);

        verify(webhookClient, never()).notifyPythonSystem(anyString(), any(Map.class));
    }
}
