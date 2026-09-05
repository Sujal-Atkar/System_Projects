package com.system_project.distributedpaymentsystem.service.recovery;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PythonDetectionWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(PythonDetectionWebhookClient.class);

    private final RestTemplate restTemplate;

    @Value("${payment.recovery.python-api-url:http://localhost:8000/api/recovery/detect}")
    private String pythonApiUrl;

    public PythonDetectionWebhookClient(RestTemplateBuilder restTemplateBuilder) {
        // Configure short timeouts to ensure this never blocks thread pools indefinitely
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Async
    public void notifyPythonSystem(String paymentId, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // The Python system expects a JSON payload containing the paymentId and basic failure info
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForObject(pythonApiUrl, request, String.class);
            log.info("Successfully notified Python system for failed payment: {}", paymentId);
        } catch (Exception ex) {
            // We intentionally swallow this exception so that a Python outage does not crash the consumer or cause infinite retries in Java.
            // Asynchronous webhook failure handling: If Python is down, it misses the webhook.
            // The Java payment transaction has already committed. Python can theoretically reconcile later, but we do not retry infinitely here.
            log.error("Failed to notify Python system for failed payment: {} - {}", paymentId, ex.getMessage());
        }
    }
}
