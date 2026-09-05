package com.system_project.distributedpaymentsystem.service.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentStatus;
import com.system_project.distributedpaymentsystem.domain.entity.PaymentType;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRisk;
import com.system_project.distributedpaymentsystem.domain.entity.RevenueRiskStatus;
import com.system_project.distributedpaymentsystem.domain.entity.RiskType;
import com.system_project.distributedpaymentsystem.repository.RevenueRiskRepository;

@ExtendWith(MockitoExtension.class)
class RevenueRiskServiceTest {

    @Mock
    private RevenueRiskRepository revenueRiskRepository;

    private RevenueRiskService revenueRiskService;

    @BeforeEach
    void setUp() {
        revenueRiskService = new RevenueRiskService(revenueRiskRepository);
    }

    @Test
    void createIfAbsent_shouldCreateRevenueRiskForFailedPayment() {
        // Given
        Payment payment = createPayment(PaymentStatus.FAILED);
        when(revenueRiskRepository.findByPayment_PaymentId(payment.getPaymentId())).thenReturn(Optional.empty());
        when(revenueRiskRepository.save(any(RevenueRisk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<RevenueRisk> result = revenueRiskService.createIfAbsent(payment);

        // Then
        assertTrue(result.isPresent());
        RevenueRisk risk = result.get();
        assertEquals(payment.getPaymentId(), risk.getPayment().getPaymentId());
        assertEquals(RevenueRiskStatus.DETECTED, risk.getStatus());
        assertEquals(RiskType.PAYMENT_FAILURE, risk.getRiskType());
        assertEquals(payment.getAmount(), risk.getRiskAmount());
        assertEquals(payment.getCurrency(), risk.getCurrency());
        verify(revenueRiskRepository).save(any(RevenueRisk.class));
    }

    @Test
    void createIfAbsent_shouldNotCreateForSuccessPayment() {
        // Given
        Payment payment = createPayment(PaymentStatus.SUCCESS);

        // When
        Optional<RevenueRisk> result = revenueRiskService.createIfAbsent(payment);

        // Then
        assertFalse(result.isPresent());
        verify(revenueRiskRepository, never()).save(any(RevenueRisk.class));
    }

    @Test
    void createIfAbsent_shouldNotCreateForUnknownPayment() {
        // Given
        Payment payment = createPayment(PaymentStatus.UNKNOWN);

        // When
        Optional<RevenueRisk> result = revenueRiskService.createIfAbsent(payment);

        // Then
        assertFalse(result.isPresent());
        verify(revenueRiskRepository, never()).save(any(RevenueRisk.class));
    }

    @Test
    void createIfAbsent_shouldBeIdempotent() {
        // Given
        Payment payment = createPayment(PaymentStatus.FAILED);
        RevenueRisk existingRisk = createRevenueRisk(payment);
        when(revenueRiskRepository.findByPayment_PaymentId(payment.getPaymentId()))
                .thenReturn(Optional.of(existingRisk));

        // When
        Optional<RevenueRisk> result1 = revenueRiskService.createIfAbsent(payment);
        Optional<RevenueRisk> result2 = revenueRiskService.createIfAbsent(payment);

        // Then
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(existingRisk.getRiskId(), result1.get().getRiskId());
        assertEquals(existingRisk.getRiskId(), result2.get().getRiskId());
        // Save should never be called since risk already exists
        verify(revenueRiskRepository, never()).save(any(RevenueRisk.class));
    }

    @Test
    void claimForDecision_shouldSuccessfullyClaimDetectedRisk() {
        // Given
        UUID riskId = UUID.randomUUID();
        when(revenueRiskRepository.updateStatusIfCurrent(
                eq(riskId),
                eq(RevenueRiskStatus.DETECTED),
                eq(RevenueRiskStatus.IN_RECOVERY)))
                .thenReturn(1); // One row updated

        // When
        ClaimResult result = revenueRiskService.claimForDecision(riskId);

        // Then
        assertTrue(result.isClaimed());
        verify(revenueRiskRepository).updateStatusIfCurrent(eq(riskId), eq(RevenueRiskStatus.DETECTED), eq(RevenueRiskStatus.IN_RECOVERY));
    }

    @Test
    void claimForDecision_shouldFailWhenRiskNotDetected() {
        // Given
        UUID riskId = UUID.randomUUID();
        when(revenueRiskRepository.updateStatusIfCurrent(
                eq(riskId),
                eq(RevenueRiskStatus.DETECTED),
                eq(RevenueRiskStatus.IN_RECOVERY)))
                .thenReturn(0); // No rows updated

        // When
        ClaimResult result = revenueRiskService.claimForDecision(riskId);

        // Then
        assertFalse(result.isClaimed());
        assertNotNull(result.getReason());
    }

    @Test
    void claimForDecision_shouldFailWithNullRiskId() {
        // When
        ClaimResult result = revenueRiskService.claimForDecision(null);

        // Then
        assertFalse(result.isClaimed());
        assertEquals("Risk ID is required", result.getReason());
        verify(revenueRiskRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    void claimForDecision_shouldFailForTerminalRisk() {
        // Given
        UUID riskId = UUID.randomUUID();
        // Simulate risk in terminal state (e.g., RECOVERED) - not DETECTED
        when(revenueRiskRepository.updateStatusIfCurrent(
                eq(riskId),
                eq(RevenueRiskStatus.DETECTED),
                eq(RevenueRiskStatus.IN_RECOVERY)))
                .thenReturn(0); // No rows updated because risk is not in DETECTED state

        // When
        ClaimResult result = revenueRiskService.claimForDecision(riskId);

        // Then
        assertFalse(result.isClaimed());
    }

    private Payment createPayment(PaymentStatus status) {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Test Merchant",
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
                status,
                "idempotency-key-" + System.nanoTime(),
                "customer-ref",
                "order-ref",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                PaymentType.PAYMENT,
                "test payment"
        );
    }

    private RevenueRisk createRevenueRisk(Payment payment) {
        return new RevenueRisk(
                UUID.randomUUID(),
                payment.getMerchant(),
                payment,
                RiskType.PAYMENT_FAILURE,
                payment.getAmount(),
                payment.getCurrency(),
                BigDecimal.valueOf(1.0),
                "Payment failed",
                RevenueRiskStatus.DETECTED,
                OffsetDateTime.now(),
                null
        );
    }
}
