package com.system_project.distributedpaymentsystem.service.outbox;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.domain.entity.OutboxEvent;
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEventStatus;
import com.system_project.distributedpaymentsystem.repository.OutboxEventRepository;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.outbox.topic:payment-events}")
    private String topic;

    @Value("${payment.outbox.batch-size:100}")
    private int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                OutboxEventStatus.PENDING,
                OffsetDateTime.now(ZoneOffset.UTC),
                PageRequest.of(0, batchSize)
        );

        for (OutboxEvent event : events) {
            try {
                String payload = objectMapper.writeValueAsString(event.getPayload());
                kafkaTemplate.send(topic, event.getEventKey(), payload).get();

                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
                event.setLastError(null);
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
                event.setRetryCount(retryCount + 1);
                event.setStatus(OutboxEventStatus.PENDING);
                event.setLastError(ex.getMessage());
                event.setAvailableAt(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(Math.min(60L, 2L * (retryCount + 1))));
                outboxEventRepository.save(event);
            }
        }
    }
}
