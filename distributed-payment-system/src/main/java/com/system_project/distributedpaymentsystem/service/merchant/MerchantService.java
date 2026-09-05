package com.system_project.distributedpaymentsystem.service.merchant;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.system_project.distributedpaymentsystem.api.merchant.dto.CreateMerchantRequest;
import com.system_project.distributedpaymentsystem.api.merchant.dto.CreateMerchantResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.GetMerchantResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.MerchantOnboardingResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.ProvisionedApiCredential;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.MerchantStatus;
import com.system_project.distributedpaymentsystem.exception.MerchantNotFoundException;
import com.system_project.distributedpaymentsystem.repository.MerchantRepository;
import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialGenerationService;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiCredentialGenerationService credentialGenerationService;

    public MerchantService(MerchantRepository merchantRepository,
            ApiCredentialGenerationService credentialGenerationService) {
        this.merchantRepository = merchantRepository;
        this.credentialGenerationService = credentialGenerationService;
    }

    @Transactional
    public MerchantOnboardingResponse createMerchantWithCredential(CreateMerchantRequest request) {
        String merchantName = request.getMerchantName() == null ? null : request.getMerchantName().trim();
        String email = request.getEmail() == null ? null : request.getEmail().trim();
        String webhookUrl = request.getWebhookUrl() == null ? null : request.getWebhookUrl().trim();
        String phone = request.getPhone() == null ? null : request.getPhone().trim();

        if (merchantName == null || merchantName.isBlank()) {
            throw new IllegalArgumentException("merchantName is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                merchantName,
                email,
                webhookUrl,
                MerchantStatus.ACTIVE,
                now,
                now,
                phone);

        Merchant savedMerchant = merchantRepository.save(merchant);
        ProvisionedApiCredential credential = credentialGenerationService.provisionTestCredential(savedMerchant);

        return new MerchantOnboardingResponse(
                savedMerchant.getMerchantId(),
                savedMerchant.getMerchantName(),
                savedMerchant.getEmail(),
                savedMerchant.getWebhookUrl(),
                savedMerchant.getPhone(),
                savedMerchant.getStatus(),
                savedMerchant.getCreatedAt(),
                savedMerchant.getUpdatedAt(),
                credential);
    }

    public CreateMerchantResponse createMerchant(CreateMerchantRequest request) {
        String merchantName = request.getMerchantName() == null ? null : request.getMerchantName().trim();
        String email = request.getEmail() == null ? null : request.getEmail().trim();
        String webhookUrl = request.getWebhookUrl() == null ? null : request.getWebhookUrl().trim();
        String phone = request.getPhone() == null ? null : request.getPhone().trim();

        if (merchantName == null || merchantName.isBlank()) {
            throw new IllegalArgumentException("merchantName is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                merchantName,
                email,
                webhookUrl,
                MerchantStatus.ACTIVE,
                now,
                now,
                phone);

        Merchant savedMerchant = merchantRepository.save(merchant);

        return new CreateMerchantResponse(
                savedMerchant.getMerchantId(),
                savedMerchant.getMerchantName(),
                savedMerchant.getEmail(),
                savedMerchant.getWebhookUrl(),
                savedMerchant.getPhone(),
                savedMerchant.getStatus(),
                savedMerchant.getCreatedAt(),
                savedMerchant.getUpdatedAt());
    }

    public GetMerchantResponse getMerchant(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        return new GetMerchantResponse(
                merchant.getMerchantId(),
                merchant.getMerchantName(),
                merchant.getEmail(),
                merchant.getWebhookUrl(),
                merchant.getPhone(),
                merchant.getStatus(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt());
    }
}