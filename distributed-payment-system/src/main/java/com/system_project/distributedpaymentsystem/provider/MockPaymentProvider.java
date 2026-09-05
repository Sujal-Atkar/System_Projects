package com.system_project.distributedpaymentsystem.provider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.service.provider.ProviderHealthService;

public class MockPaymentProvider implements PaymentProvider {

    public enum MockFinalStatus {
        SUCCESS,
        FAILED,
        STILL_UNKNOWN
    }

    private static final String TIMEOUT_TRIGGER = "TIMEOUT";
    private static final String FAIL_TRIGGER = "FAIL";
    private final Map<String, MockFinalStatus> finalStatusByReference = new ConcurrentHashMap<>();
    private final ProviderHealthService providerHealthService;

    public MockPaymentProvider() {
        this(null);
    }

    public MockPaymentProvider(ProviderHealthService providerHealthService) {
        this.providerHealthService = providerHealthService;
    }

    void setFinalStatusForReference(String providerReference, MockFinalStatus finalStatus) {
        if (providerReference == null || providerReference.isBlank()) {
            return;
        }
        finalStatusByReference.put(providerReference, finalStatus);
    }

    MockFinalStatus getFinalStatusForReference(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return null;
        }
        return finalStatusByReference.get(providerReference);
    }

    @Override
    public ProviderProcessingResult processPayment(Payment payment, Provider provider) {
        String customerRef = payment.getCustomerRef();
        String orderRef = payment.getOrderRef();

        if (customerRef != null && customerRef.contains(TIMEOUT_TRIGGER)
                || orderRef != null && orderRef.contains(TIMEOUT_TRIGGER)) {
            return ProviderProcessingResult.timeout("MOCK-TIMEOUT-" + UUID.randomUUID(), "TIMEOUT", "provider timeout while waiting for final outcome");
        }
        if ("PROVIDER_A".equals(provider.getProviderCode()) && ((customerRef != null && customerRef.contains(FAIL_TRIGGER)) || (orderRef != null && orderRef.contains(FAIL_TRIGGER)))) {
            return ProviderProcessingResult.failed("MOCK-FAIL-" + UUID.randomUUID(), "GATEWAY_DECLINE", "provider declined transaction");
        }

        String reference = "MOCK-" + UUID.randomUUID();
        return new ProviderProcessingResult(true, reference, null, null);
    }

    @Override
    public ProviderProcessingResult getPaymentStatus(Payment payment, Provider provider, String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return ProviderProcessingResult.failed("UNKNOWN", "UNKNOWN", "missing provider reference");
        }

        MockFinalStatus configuredStatus = finalStatusByReference.get(providerReference);
        if (configuredStatus != null) {
            if (configuredStatus == MockFinalStatus.SUCCESS) {
                return ProviderProcessingResult.success(providerReference, null, "provider confirmed success");
            }
            if (configuredStatus == MockFinalStatus.FAILED) {
                return ProviderProcessingResult.failed(providerReference, "FAILED", "provider confirmed failure");
            }
            return ProviderProcessingResult.failed(providerReference, "UNKNOWN", "provider could not determine final status");
        }

        String reference = providerReference.toUpperCase();
        if (reference.contains("SUCCESS")) {
            return ProviderProcessingResult.success(providerReference, null, "provider confirmed success");
        }
        if (reference.contains("FAILED")) {
            return ProviderProcessingResult.failed(providerReference, "FAILED", "provider confirmed failure");
        }

        return ProviderProcessingResult.failed(providerReference, "UNKNOWN", "provider could not determine final status");
    }

    @Override
    public void recordSuccess(Provider provider) {
        if (providerHealthService != null) {
            providerHealthService.recordOutcome(provider, ProviderProcessingResult.success("provider-success", null, "provider call succeeded"));
        }
    }

    @Override
    public void recordFailure(Provider provider) {
        if (providerHealthService != null) {
            providerHealthService.recordOutcome(provider, ProviderProcessingResult.timeout("provider-timeout", "TIMEOUT", "provider technical failure"));
        }
    }
}
