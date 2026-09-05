package com.system_project.distributedpaymentsystem.service.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;

@Component
public class ProviderCircuitBreaker {

    private static final class CircuitEntry {
        private volatile ProviderCircuitState state = ProviderCircuitState.CLOSED;
        private int failureCount;
        private Instant openedAt;
        private boolean halfOpenProbeInFlight;
    }

    private final int failureThreshold;
    private final Duration cooldown;
    private final Map<UUID, CircuitEntry> stateByProvider = new ConcurrentHashMap<>();

    public ProviderCircuitBreaker(
            @Value("${payment.provider.circuit.failure-threshold:3}") int failureThreshold,
            @Value("${payment.provider.circuit.cooldown-duration:30s}") Duration cooldown) {
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
    }

    public ProviderCircuitBreaker() {
        this(3, Duration.ofSeconds(30));
    }

    public boolean allowRequest(UUID providerId) {
        CircuitEntry entry = stateByProvider.computeIfAbsent(providerId, ignored -> new CircuitEntry());
        Instant now = Instant.now();

        if (entry.state == ProviderCircuitState.OPEN) {
            if (entry.openedAt != null && Duration.between(entry.openedAt, now).compareTo(cooldown) >= 0) {
                entry.state = ProviderCircuitState.HALF_OPEN;
                entry.halfOpenProbeInFlight = true;
                return true;
            }
            return false;
        }

        if (entry.state == ProviderCircuitState.HALF_OPEN) {
            if (entry.halfOpenProbeInFlight) {
                return false;
            }
            entry.halfOpenProbeInFlight = true;
            return true;
        }

        return true;
    }

    public void recordSuccess(UUID providerId) {
        CircuitEntry entry = stateByProvider.computeIfAbsent(providerId, ignored -> new CircuitEntry());
        entry.failureCount = 0;
        entry.halfOpenProbeInFlight = false;
        entry.openedAt = null;
        entry.state = ProviderCircuitState.CLOSED;
    }

    public void recordFailure(UUID providerId) {
        CircuitEntry entry = stateByProvider.computeIfAbsent(providerId, ignored -> new CircuitEntry());

        if (entry.state == ProviderCircuitState.HALF_OPEN) {
            entry.state = ProviderCircuitState.OPEN;
            entry.failureCount = failureThreshold;
            entry.openedAt = Instant.now();
            entry.halfOpenProbeInFlight = false;
            return;
        }

        if (entry.state == ProviderCircuitState.OPEN) {
            entry.openedAt = entry.openedAt == null ? Instant.now() : entry.openedAt;
            return;
        }

        entry.failureCount++;
        if (entry.failureCount >= failureThreshold) {
            entry.state = ProviderCircuitState.OPEN;
            entry.openedAt = Instant.now();
        }
    }

    public boolean shouldCountAsTechnicalFailure(ProviderProcessingResult result) {
        if(result == null){
            return false;
        }

        if (result.isTimeout()) {
            return true;
        }

        String errorCode = result.getErrorCode();
        if (errorCode == null) {
            return false;
        }

        String normalized = errorCode.trim().toUpperCase();
        return "TIMEOUT".equals(normalized)
                || "UNKNOWN".equals(normalized)
                || "UNAVAILABLE".equals(normalized)
                || "CONNECTION_ERROR".equals(normalized);
    }

    public ProviderCircuitState getState(UUID providerId) {
        return stateByProvider.getOrDefault(providerId, new CircuitEntry()).state;
    }
}
