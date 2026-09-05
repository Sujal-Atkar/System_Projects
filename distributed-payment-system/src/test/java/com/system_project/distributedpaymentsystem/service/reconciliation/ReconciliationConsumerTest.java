package com.system_project.distributedpaymentsystem.service.reconciliation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationConsumerTest {

    @Mock
    private ReconciliationService reconciliationService;

    private ReconciliationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReconciliationConsumer(reconciliationService, new ObjectMapper());
    }

    @Test
    void processPaymentEvent_shouldTriggerReconciliationForPaymentUnknown() {
        UUID paymentId = UUID.randomUUID();
        String payload = unknownPayload(paymentId);

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertTrue(processed);
        verify(reconciliationService).reconcilePayment(paymentId);
    }

    @Test
    void processPaymentEvent_shouldIgnorePaymentSucceeded() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"SUCCESS"}
                """.formatted(paymentId, UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertFalse(processed);
        verify(reconciliationService, never()).reconcilePayment(any());
    }

    @Test
    void processPaymentEvent_shouldIgnorePaymentFailed() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"FAILED"}
                """.formatted(paymentId, UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertFalse(processed);
        verify(reconciliationService, never()).reconcilePayment(any());
    }

    @Test
    void processPaymentEvent_shouldRejectInvalidPaymentId() {
        String payload = """
                {"paymentId":"not-a-uuid","merchantId":"%s","amount":100.00,"currency":"USD","status":"UNKNOWN"}
                """.formatted(UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent("not-a-uuid", payload);

        assertFalse(processed);
        verify(reconciliationService, never()).reconcilePayment(any());
    }

    @Test
    void processPaymentEvent_shouldRejectKafkaKeyPaymentIdMismatch() {
        String payload = unknownPayload(UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent(UUID.randomUUID().toString(), payload);

        assertFalse(processed);
        verify(reconciliationService, never()).reconcilePayment(any());
    }

    @Test
    void processPaymentEvent_shouldDelegateDuplicateUnknownEventsToReconciliationService() {
        UUID paymentId = UUID.randomUUID();
        String payload = unknownPayload(paymentId);

        assertTrue(consumer.processPaymentEvent(paymentId.toString(), payload));
        assertTrue(consumer.processPaymentEvent(paymentId.toString(), payload));

        verify(reconciliationService, times(2)).reconcilePayment(paymentId);
    }

    @Test
    void processPaymentEvent_shouldHandleAlreadyResolvedPaymentSafely() {
        UUID paymentId = UUID.randomUUID();
        String payload = unknownPayload(paymentId);
        when(reconciliationService.reconcilePayment(paymentId))
                .thenThrow(new IllegalStateException("Payment is not UNKNOWN and cannot be reconciled: " + paymentId));

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertFalse(processed);
        verify(reconciliationService).reconcilePayment(paymentId);
    }

    @Test
    void processPaymentEvent_shouldPropagateReconciliationFailureForKafkaRetry() {
        UUID paymentId = UUID.randomUUID();
        String payload = unknownPayload(paymentId);
        when(reconciliationService.reconcilePayment(paymentId))
                .thenThrow(new RuntimeException("provider status unavailable"));

        assertThrows(RuntimeException.class, () -> consumer.processPaymentEvent(paymentId.toString(), payload));
    }

    @Test
    void processPaymentEvent_shouldIgnoreMalformedPayload() {
        boolean processed = consumer.processPaymentEvent(UUID.randomUUID().toString(), "{");

        assertFalse(processed);
        verify(reconciliationService, never()).reconcilePayment(any());
    }

    private String unknownPayload(UUID paymentId) {
        return """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"UNKNOWN"}
                """.formatted(paymentId, UUID.randomUUID());
    }
}
