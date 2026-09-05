package com.system_project.distributedpaymentsystem.service.recovery;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_project.distributedpaymentsystem.config.GeminiRecoveryConfig;

@Component
public class GeminiRecoveryDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiRecoveryDecisionClient.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    private final GeminiRecoveryConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiRecoveryDecisionClient(GeminiRecoveryConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    /**
     * Call Gemini API to get recovery decision.
     *
     * @param context the recovery context with allowed actions
     * @return GeminiDecisionResult with decision, reasoning, and confidence
     */
    public GeminiDecisionResult requestDecision(AIRecoveryContext context) {
        // Check if API key is configured
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            log.warn("Gemini API key not configured; returning failure result");
            return GeminiDecisionResult.failure("Gemini API key not configured");
        }

        try {
            // Build request
            String userMessage = buildUserMessage(context);
            Map<String, Object> requestBody = buildRequestBody(userMessage);

            // Make API call with timeout handling
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = callGeminiAPI(requestBody);
            long elapsedMs = System.currentTimeMillis() - startTime;

            // Check timeout
            int timeoutMs = (config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 5) * 1000;
            if (elapsedMs > timeoutMs) {
                log.warn("Gemini call exceeded timeout: {} ms > {} ms", elapsedMs, timeoutMs);
                return GeminiDecisionResult.failure("Gemini call timeout");
            }

            // Parse response
            String responseText = response.getBody();
            return parseGeminiResponse(responseText, context);

        } catch (RestClientException e) {
            log.error("Gemini API error: {}", e.getMessage());
            return GeminiDecisionResult.failure("Gemini API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling Gemini: {}", e.getMessage(), e);
            return GeminiDecisionResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRequestBody(String userMessage) {
        Map<String, Object> request = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        List<Map<String, String>> parts = List.of(Map.of("text", userMessage));

        content.put("parts", parts);
        request.put("contents", List.of(content));

        return request;
    }

    private ResponseEntity<String> callGeminiAPI(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = GEMINI_API_URL.replace("{model}", config.getModel())
                + "?key=" + config.getApiKey();

        return restTemplate.postForEntity(url, entity, String.class);
    }

    private String buildUserMessage(AIRecoveryContext context) {
        return String.format(
                "You are a payment recovery decision system. You must choose EXACTLY ONE action from: %s\n" +
                "Payment Recovery Decision:\n" +
                "Amount: %s %s\n" +
                "Failure Type: %s\n" +
                "Failure Reason: %s\n" +
                "Provider: %s\n" +
                "Attempt Number: %d\n" +
                "Risk Score: %.2f\n\n" +
                "Respond ONLY with this JSON structure (no other text):\n" +
                "{\"decision\": \"<ACTION>\", \"reasoning\": \"<brief reason>\", \"confidence\": <0.0-1.0>}",
                String.join(", ", context.getAllowedActions()),
                context.getPaymentAmount(),
                context.getCurrency(),
                context.getFailureType(),
                context.getFailureReason(),
                context.getProviderCode(),
                context.getAttemptNumber(),
                context.getRiskScore()
        );
    }

    private GeminiDecisionResult parseGeminiResponse(String responseText, AIRecoveryContext context) {
        try {
            // Parse Gemini API response structure
            JsonNode rootNode = objectMapper.readTree(responseText);
            JsonNode candidates = rootNode.get("candidates");

            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                return GeminiDecisionResult.failure("No candidates in Gemini response");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode contentNode = firstCandidate.get("content");
            JsonNode parts = contentNode.get("parts");

            if (parts == null || !parts.isArray() || parts.size() == 0) {
                return GeminiDecisionResult.failure("No parts in Gemini response");
            }

            String textContent = parts.get(0).get("text").asText();

            // Extract JSON from text content
            String jsonString = extractJsonFromResponse(textContent);
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            String decision = jsonNode.get("decision").asText();
            String reasoning = jsonNode.get("reasoning").asText();
            double confidence = jsonNode.get("confidence").asDouble();

            // Validate decision is in allowed actions
            if (!context.getAllowedActions().contains(decision)) {
                log.warn("Gemini returned disallowed action: {} (allowed: {})", decision, context.getAllowedActions());
                return GeminiDecisionResult.failure("Decision not in allowed actions");
            }

            return GeminiDecisionResult.success(
                    decision,
                    reasoning,
                    BigDecimal.valueOf(confidence)
            );

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return GeminiDecisionResult.failure("Failed to parse response: " + e.getMessage());
        }
    }

    private String extractJsonFromResponse(String responseText) {
        // Try to find JSON block in response
        int jsonStart = responseText.indexOf('{');
        int jsonEnd = responseText.lastIndexOf('}');

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return responseText.substring(jsonStart, jsonEnd + 1);
        }

        throw new IllegalArgumentException("No JSON found in response");
    }
}
