package com.system_project.distributedpaymentsystem.provider;

import org.springframework.stereotype.Component;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.service.provider.ProviderHealthService;

@Component
public class DefaultPaymentProviderAdapter implements PaymentProvider {

    private final MockPaymentProvider mockPaymentProvider;

    public DefaultPaymentProviderAdapter(ProviderHealthService providerHealthService) {
        this.mockPaymentProvider = new MockPaymentProvider(providerHealthService);
    }

    @Override
    public ProviderProcessingResult processPayment(Payment payment, Provider provider) {
        return mockPaymentProvider.processPayment(payment, provider);
    }

    @Override
    public ProviderProcessingResult getPaymentStatus(Payment payment, Provider provider, String providerReference) {
        return mockPaymentProvider.getPaymentStatus(payment, provider, providerReference);
    }

    @Override
    public void recordSuccess(Provider provider) {
        mockPaymentProvider.recordSuccess(provider);
    }

    @Override
    public void recordFailure(Provider provider) {
        mockPaymentProvider.recordFailure(provider);
    }
}
