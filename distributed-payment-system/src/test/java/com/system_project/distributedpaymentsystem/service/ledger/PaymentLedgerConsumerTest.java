package com.system_project.distributedpaymentsystem.service.ledger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentLedgerConsumerTest {

    @Mock
    private LedgerService ledgerService;

    private PaymentLedgerConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentLedgerConsumer(ledgerService, new ObjectMapper());
    }

    @Test
    void processPaymentEvent_shouldPostLedgerForPaymentSucceeded() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"SUCCESS"}
                """.formatted(paymentId, merchantId);

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertTrue(processed);
        ArgumentCaptor<PaymentLedgerEvent> eventCaptor = ArgumentCaptor.forClass(PaymentLedgerEvent.class);
        verify(ledgerService).postSuccessfulPayment(eventCaptor.capture());
        assertTrue(paymentId.equals(eventCaptor.getValue().getPaymentId()));
    }

    @Test
    void processPaymentEvent_shouldIgnorePaymentFailed() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"FAILED"}
                """.formatted(paymentId, UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertFalse(processed);
        verify(ledgerService, never()).postSuccessfulPayment(any());
    }

    @Test
    void processPaymentEvent_shouldIgnorePaymentUnknown() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"UNKNOWN"}
                """.formatted(paymentId, UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertFalse(processed);
        verify(ledgerService, never()).postSuccessfulPayment(any());
    }

    @Test
    void processPaymentEvent_shouldIgnoreMalformedEvent() {
        boolean processed = consumer.processPaymentEvent(UUID.randomUUID().toString(),
                "{\"paymentId\":\"missing-fields\"}");

        assertFalse(processed);
        verify(ledgerService, never()).postSuccessfulPayment(any());
    }

    @Test
    void processPaymentEvent_shouldRejectMismatchedKafkaKey() {
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"SUCCESS"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        boolean processed = consumer.processPaymentEvent(UUID.randomUUID().toString(), payload);

        assertFalse(processed);
        verify(ledgerService, never()).postSuccessfulPayment(any());
    }

    @Test
    void processPaymentEvent_shouldProcessMultipleDifferentPayments() {
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();
        String firstPayload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"SUCCESS"}
                """.formatted(firstPaymentId, UUID.randomUUID());
        String secondPayload = """
                {"paymentId":"%s","merchantId":"%s","amount":42.00,"currency":"USD","status":"SUCCESS"}
                """.formatted(secondPaymentId, UUID.randomUUID());

        assertTrue(consumer.processPaymentEvent(firstPaymentId.toString(), firstPayload));
        assertTrue(consumer.processPaymentEvent(secondPaymentId.toString(), secondPayload));

        verify(ledgerService, org.mockito.Mockito.times(2)).postSuccessfulPayment(any());
    }

    @Test
    void processPaymentEvent_shouldPropagateLedgerFailureForKafkaRetry() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"paymentId":"%s","merchantId":"%s","amount":100.00,"currency":"USD","status":"SUCCESS"}
                """.formatted(paymentId, UUID.randomUUID());
        when(ledgerService.postSuccessfulPayment(any())).thenThrow(new RuntimeException("ledger unavailable"));

        assertThrows(RuntimeException.class, () -> consumer.processPaymentEvent(paymentId.toString(), payload));
    }
}
