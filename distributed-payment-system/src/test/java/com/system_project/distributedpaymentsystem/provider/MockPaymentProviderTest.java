package com.system_project.distributedpaymentsystem.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentType;
import com.system_project.distributedpaymentsystem.domain.entity.Provider;
import com.system_project.distributedpaymentsystem.domain.entity.ProviderStatus;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider = new MockPaymentProvider();

    @Test
    void getPaymentStatus_shouldReturnSuccess_whenReferenceHasConfiguredSuccess() {
        String reference = "MOCK-TIMEOUT-" + UUID.randomUUID();
        provider.setFinalStatusForReference(reference, MockPaymentProvider.MockFinalStatus.SUCCESS);

        ProviderProcessingResult result = provider.getPaymentStatus(createPayment(), createProvider(), reference);

        assertTrue(result.isSuccess());
        assertEquals(ProviderProcessingResult.ProviderOutcome.SUCCESS, result.getOutcome());
    }

    @Test
    void getPaymentStatus_shouldReturnFailed_whenReferenceHasConfiguredFailure() {
        String reference = "MOCK-TIMEOUT-" + UUID.randomUUID();
        provider.setFinalStatusForReference(reference, MockPaymentProvider.MockFinalStatus.FAILED);

        ProviderProcessingResult result = provider.getPaymentStatus(createPayment(), createProvider(), reference);

        assertTrue(result.isFailed());
        assertEquals(ProviderProcessingResult.ProviderOutcome.FAILED, result.getOutcome());
    }

    @Test
    void getPaymentStatus_shouldRemainUnresolved_whenReferenceHasConfiguredStillUnknown() {
        String reference = "MOCK-TIMEOUT-" + UUID.randomUUID();
        provider.setFinalStatusForReference(reference, MockPaymentProvider.MockFinalStatus.STILL_UNKNOWN);

        ProviderProcessingResult result = provider.getPaymentStatus(createPayment(), createProvider(), reference);

        assertFalse(result.isSuccess());
        assertTrue(result.isFailed());
        assertEquals(ProviderProcessingResult.ProviderOutcome.FAILED, result.getOutcome());
        assertEquals("UNKNOWN", result.getErrorCode());
    }

    @Test
    void getPaymentStatus_shouldRemainUnresolved_whenNoConfiguredFinalStatusExists() {
        String reference = "MOCK-TIMEOUT-" + UUID.randomUUID();

        ProviderProcessingResult result = provider.getPaymentStatus(createPayment(), createProvider(), reference);

        assertTrue(result.isFailed());
        assertEquals("UNKNOWN", result.getErrorCode());
    }

    private Payment createPayment() {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Merchant",
                "merchant@test.com",
                null,
                MerchantStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        return new Payment(
                UUID.randomUUID(),
                merchant,
                new BigDecimal("100.00"),
                "USD",
                PaymentStatus.UNKNOWN,
                "idem-mock-test",
                "CUST-MOCK",
                "ORDER-MOCK",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                PaymentType.PAYMENT,
                "mock provider test"
        );
    }

    private Provider createProvider() {
        return new Provider(
                UUID.randomUUID(),
                "Test Provider",
                "test-provider",
                1,
                ProviderStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
