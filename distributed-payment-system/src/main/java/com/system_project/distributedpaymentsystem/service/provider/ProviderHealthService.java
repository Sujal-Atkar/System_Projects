package com.system_project.distributedpaymentsystem.service.provider;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;

@Service
public class ProviderHealthService {

    private final ProviderCircuitBreaker circuitBreaker;

    public ProviderHealthService(ProviderCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public ProviderHealthService() {
        this(new ProviderCircuitBreaker());
    }

    public boolean isRequestAllowed(Provider provider) {
        if (provider == null) {
            return false;
        }
        return circuitBreaker.allowRequest(provider.getProviderId());
    }

    public void recordOutcome(Provider provider, ProviderProcessingResult result) {
        if (provider == null || result == null) {
            return;
        }
        UUID providerId = provider.getProviderId();
        if (result.isSuccess()) {
            circuitBreaker.recordSuccess(providerId);
            return;
        }
        if (circuitBreaker.shouldCountAsTechnicalFailure(result)) {
            circuitBreaker.recordFailure(providerId);
        }
    }

    public ProviderCircuitState getState(Provider provider) {
        if (provider == null) {
            return ProviderCircuitState.CLOSED;
        }
        return circuitBreaker.getState(provider.getProviderId());
    }
}
