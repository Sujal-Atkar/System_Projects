package com.system_project.distributedpaymentsystem.service.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.system_project.distributedpaymentsystem.domain.entity.OutboxEvent;
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEventStatus;
import com.system_project.distributedpaymentsystem.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(outboxPublisher, "topic", "payment-events");
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
    }

    @Test
    void shouldPublishPendingEventAndMarkPublished() throws Exception {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT",
                UUID.randomUUID(),
                "PAYMENT_SUCCEEDED",
                "payment-id",
                Map.of("paymentId", "abc"),
                OutboxEventStatus.PENDING,
                0,
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(outboxEventRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq(OutboxEventStatus.PENDING), any(OffsetDateTime.class), any(PageRequest.class)))
                .thenReturn(java.util.List.of(event));
        when(kafkaTemplate.send(eq("payment-events"), eq("payment-id"), eq("{\"paymentId\":\"abc\"}")))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxPublisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void shouldKeepEventPendingWhenPublishFails() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT",
                UUID.randomUUID(),
                "PAYMENT_FAILED",
                "payment-id",
                Map.of("paymentId", "abc"),
                OutboxEventStatus.PENDING,
                0,
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(outboxEventRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq(OutboxEventStatus.PENDING), any(OffsetDateTime.class), any(PageRequest.class)))
                .thenReturn(java.util.List.of(event));
        when(kafkaTemplate.send(eq("payment-events"), eq("payment-id"), any()))
                .thenThrow(new RuntimeException("broker unavailable"));

        outboxPublisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        verify(outboxEventRepository).save(event);
    }
}
