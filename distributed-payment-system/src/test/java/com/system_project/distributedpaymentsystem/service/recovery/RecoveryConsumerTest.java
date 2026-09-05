package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.system_project.distributedpaymentsystem.domain.entity.RecoveryAction;
import com.system_project.distributedpaymentsystem.domain.entity.RecoveryActionStatus;

@ExtendWith(MockitoExtension.class)
class RecoveryConsumerTest {

    @Mock
    private RecoveryService recoveryService;

    @Test
    void shouldProcessFailedPaymentEvent() {
        RecoveryConsumer consumer = new RecoveryConsumer(recoveryService, new ObjectMapper());
        UUID paymentId = UUID.randomUUID();
        String payload = "{\"eventType\":\"PAYMENT_FAILED\",\"status\":\"FAILED\",\"paymentId\":\"" + paymentId + "\"}";

        when(recoveryService.processFailedPayment(paymentId)).thenReturn(new RecoveryAction(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), RecoveryActionType.RETRY, null,
                RecoveryActionStatus.PENDING, 1, null, null, "retryable outage"));

        boolean result = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertTrue(result);
        verify(recoveryService).processFailedPayment(paymentId);
    }

    @Test
    void shouldIgnoreSuccessfulPaymentEvent() {
        RecoveryConsumer consumer = new RecoveryConsumer(recoveryService, new ObjectMapper());
        UUID paymentId = UUID.randomUUID();
        String payload = "{\"eventType\":\"PAYMENT_SUCCEEDED\",\"status\":\"SUCCESS\",\"paymentId\":\"" + paymentId + "\"}";

        boolean result = consumer.processPaymentEvent(paymentId.toString(), payload);

        assertFalse(result);
        verify(recoveryService, never()).processFailedPayment(any(UUID.class));
    }
}
