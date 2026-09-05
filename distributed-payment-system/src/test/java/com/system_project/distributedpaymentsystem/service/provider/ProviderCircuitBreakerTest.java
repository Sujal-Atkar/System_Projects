package com.system_project.distributedpaymentsystem.service.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ProviderStatus;
import com.system_project.distributedpaymentsystem.provider.ProviderProcessingResult;
import com.system_project.distributedpaymentsystem.repository.ProviderRepository;

class ProviderCircuitBreakerTest {

    @Test
    void shouldOpenCircuitAfterFailureThreshold() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(2, Duration.ofSeconds(1));
        UUID providerId = UUID.randomUUID();

        assertTrue(breaker.allowRequest(providerId));
        breaker.recordFailure(providerId);
        assertEquals(ProviderCircuitState.CLOSED, breaker.getState(providerId));

        breaker.recordFailure(providerId);
        assertEquals(ProviderCircuitState.OPEN, breaker.getState(providerId));
        assertFalse(breaker.allowRequest(providerId));
    }

    @Test
    void shouldMoveToHalfOpenAfterCooldown() throws InterruptedException {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(1, Duration.ofMillis(20));
        UUID providerId = UUID.randomUUID();

        breaker.recordFailure(providerId);
        assertEquals(ProviderCircuitState.OPEN, breaker.getState(providerId));

        Thread.sleep(50);
        assertTrue(breaker.allowRequest(providerId));
        assertEquals(ProviderCircuitState.HALF_OPEN, breaker.getState(providerId));
    }

    @Test
    void shouldCloseCircuitAfterHalfOpenSuccess() throws InterruptedException {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(1, Duration.ofMillis(20));
        UUID providerId = UUID.randomUUID();

        breaker.recordFailure(providerId);
        Thread.sleep(50);
        breaker.allowRequest(providerId);
        breaker.recordSuccess(providerId);

        assertEquals(ProviderCircuitState.CLOSED, breaker.getState(providerId));
    }

    @Test
    void shouldTreatTimeoutAsTechnicalFailure() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(1, Duration.ofSeconds(1));
        assertTrue(breaker.shouldCountAsTechnicalFailure(ProviderProcessingResult.timeout("ref", "TIMEOUT", "timeout")));
        assertFalse(breaker.shouldCountAsTechnicalFailure(ProviderProcessingResult.failed("ref", "DECLINED", "customer declined")));
    }

    @Test
    void shouldFilterOpenProvidersInRouter() {
        ProviderRepository repository = Mockito.mock(ProviderRepository.class);
        ProviderHealthService healthService = Mockito.mock(ProviderHealthService.class);
        ProviderRouter router = new ProviderRouter(repository, healthService);

        UUID providerA = UUID.randomUUID();
        UUID providerB = UUID.randomUUID();

        Provider a = new Provider(providerA, "Provider A", "provider-a", 10, ProviderStatus.ACTIVE, null, null);
        Provider b = new Provider(providerB, "Provider B", "provider-b", 20, ProviderStatus.ACTIVE, null, null);

        Mockito.when(repository.findAll()).thenReturn(java.util.List.of(a, b));
        Mockito.when(healthService.isRequestAllowed(a)).thenReturn(false);
        Mockito.when(healthService.isRequestAllowed(b)).thenReturn(true);

        assertEquals(b, router.selectProvider(UUID.randomUUID()));
    }
}
