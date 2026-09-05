package com.system_project.distributedpaymentsystem.service.provider;

import java.util.Comparator;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ProviderStatus;
import com.system_project.distributedpaymentsystem.exception.NoEligibleProviderException;
import com.system_project.distributedpaymentsystem.repository.ProviderRepository;

@Component
public class ProviderRouter {

    private final ProviderRepository providerRepository;
    private final ProviderHealthService providerHealthService;

    public ProviderRouter(ProviderRepository providerRepository, ProviderHealthService providerHealthService) {
        this.providerRepository = providerRepository;
        this.providerHealthService = providerHealthService;
    }

    public Provider selectProvider(UUID merchantId) {
        return providerRepository.findAll().stream()
                .filter(provider -> provider.getStatus() == ProviderStatus.ACTIVE)
                .filter(provider -> providerHealthService.isRequestAllowed(provider))
                .sorted(Comparator.comparingInt(Provider::getPriority).thenComparing(Provider::getProviderCode))
                .findFirst()
                .orElseThrow(() -> new NoEligibleProviderException(
                        "No active provider available for payment processing for merchant: " + merchantId));
    }
}
