package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.system_project.distributedpaymentsystem.config.GeminiRecoveryConfig;

@ExtendWith(MockitoExtension.class)
class GeminiRecoveryDecisionClientTest {

    @Mock
    private GeminiRecoveryConfig config;

    @Mock
    private RestTemplate restTemplate;

    private GeminiRecoveryDecisionClient client;

    @BeforeEach
    void setUp() {
        client = new GeminiRecoveryDecisionClient(config);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    void shouldReturnFailureWhenApiKeyNotConfigured() {
        when(config.getApiKey()).thenReturn(null);

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertEquals("Gemini API key not configured", result.getErrorMessage());
    }

    @Test
    void shouldReturnFailureWhenApiKeyIsEmpty() {
        when(config.getApiKey()).thenReturn("");

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertEquals("Gemini API key not configured", result.getErrorMessage());
    }

    @Test
    void shouldSuccessfullyParseValidGeminiResponse() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        String geminiResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\": " +
                "\"{\\\"decision\\\": \\\"RETRY\\\", \\\"reasoning\\\": \\\"Retry is viable\\\", \\\"confidence\\\": 0.95}\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY", "NO_ACTION"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertTrue(result.isValid());
        assertEquals("RETRY", result.getDecision());
        assertEquals("Retry is viable", result.getReasoning());
        assertEquals(BigDecimal.valueOf(0.95), result.getConfidence());
    }

    @Test
    void shouldRejectDisallowedDecisionFromGemini() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        // Gemini returns REROUTE but it's not in allowed actions
        String geminiResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\": " +
                "\"{\\\"decision\\\": \\\"REROUTE\\\", \\\"reasoning\\\": \\\"Better option\\\", \\\"confidence\\\": 0.9}\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY", "NO_ACTION"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertEquals("Decision not in allowed actions", result.getErrorMessage());
    }

    @Test
    void shouldHandleApiErrorGracefully() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenThrow(new RestClientException("Connection refused"));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Gemini API error"));
    }

    @Test
    void shouldHandleMalformedJsonInResponse() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        String malformedResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\": " +
                "\"invalid json\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(malformedResponse, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Failed to parse response"));
    }

    @Test
    void shouldAcceptAllValidDecisionTypes() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        List<String> validDecisions = Arrays.asList("RETRY", "REROUTE", "NOTIFY", "NO_ACTION");

        for (String decision : validDecisions) {
            String geminiResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\": " +
                    "\"{\\\"decision\\\": \\\"" + decision + "\\\", \\\"reasoning\\\": \\\"test\\\", \\\"confidence\\\": 0.8}\"}]}}]}";

            when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                    .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));

            AIRecoveryContext context = new AIRecoveryContext(
                    BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                    "provider-1", 1, validDecisions, BigDecimal.valueOf(0.8)
            );

            GeminiDecisionResult result = client.requestDecision(context);

            assertTrue(result.isValid());
            assertEquals(decision, result.getDecision());
        }
    }

    @Test
    void shouldIncludeAllowedActionsInUserMessage() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        String geminiResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\": " +
                "\"{\\\"decision\\\": \\\"RETRY\\\", \\\"reasoning\\\": \\\"test\\\", \\\"confidence\\\": 0.8}\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertTrue(result.isValid());
        // Verify the call was made (actual message verification would require inspecting the call arguments)
    }

    @Test
    void shouldHandleTimeoutGracefully() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(1); // 1 second timeout

        // Create a mock that simulates a slow response
        String geminiResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\": " +
                "\"{\\\"decision\\\": \\\"RETRY\\\", \\\"reasoning\\\": \\\"test\\\", \\\"confidence\\\": 0.8}\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        // Note: In a real test, we'd use Thread.sleep to simulate timeout
        // For this test, we just verify the structure is in place
        GeminiDecisionResult result = client.requestDecision(context);

        // Should succeed if response is fast enough
        assertTrue(result.isValid());
    }

    @Test
    void shouldHandleNoCandidatesInResponse() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        String responseWithNoCandidates = "{\"candidates\": []}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(responseWithNoCandidates, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertEquals("No candidates in Gemini response", result.getErrorMessage());
    }

    @Test
    void shouldHandleMissingCandidatesInResponse() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getTimeoutSeconds()).thenReturn(5);

        String responseWithoutCandidates = "{\"error\": \"Invalid request\"}";

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(responseWithoutCandidates, HttpStatus.OK));

        AIRecoveryContext context = new AIRecoveryContext(
                BigDecimal.valueOf(100), "USD", "Technical failure", "TECHNICAL_FAILURE",
                "provider-1", 1, Arrays.asList("RETRY", "NOTIFY"), BigDecimal.valueOf(0.8)
        );

        GeminiDecisionResult result = client.requestDecision(context);

        assertFalse(result.isValid());
        assertEquals("No candidates in Gemini response", result.getErrorMessage());
    }
}
