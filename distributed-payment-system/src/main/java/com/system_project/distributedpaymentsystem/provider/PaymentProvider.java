package com.system_project.distributedpaymentsystem.provider;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;

public interface PaymentProvider {

    ProviderProcessingResult processPayment(Payment payment, Provider provider);

    ProviderProcessingResult getPaymentStatus(Payment payment, Provider provider, String providerReference);

    default void recordSuccess(Provider provider) {
        // default no-op; concrete providers may override if they track health/circuit state
    }

    default void recordFailure(Provider provider) {
        // default no-op; concrete providers may override if they track health/circuit state
    }
}
