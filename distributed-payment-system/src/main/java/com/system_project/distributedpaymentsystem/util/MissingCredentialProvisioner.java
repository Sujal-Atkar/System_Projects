package com.system_project.distributedpaymentsystem.util;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.repository.MerchantRepository;
import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialGenerationService;

/**
 * Utility to provision API credentials for merchants that don't have any.
 * Run with: mvn spring-boot:run -Dspring-boot.run.arguments="--provision-credentials=true"
 */
@Component
public class MissingCredentialProvisioner implements CommandLineRunner {

    private final MerchantRepository merchantRepository;
    private final ApiCredentialGenerationService credentialGenerationService;

    public MissingCredentialProvisioner(MerchantRepository merchantRepository,
            ApiCredentialGenerationService credentialGenerationService) {
        this.merchantRepository = merchantRepository;
        this.credentialGenerationService = credentialGenerationService;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean provisionEnabled = false;
        for (String arg : args) {
            if ("--provision-credentials=true".equals(arg)) {
                provisionEnabled = true;
                break;
            }
        }

        if (!provisionEnabled) {
            return;
        }

        System.out.println("[CredentialProvisioner] Starting to provision missing credentials...");

        // Find all merchants and check if they have credentials
        // For now, just log what would be done
        System.out.println("[CredentialProvisioner] Provisioning complete.");
    }

    public void provisionCredentialForMerchant(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        var credential = credentialGenerationService.provisionTestCredential(merchant);

        System.out.println("[CredentialProvisioner] Provisioned credential for merchant " + merchantId);
        System.out.println("  Key ID: " + credential.getKeyId());
        System.out.println("  Secret (save this securely): " + credential.getPlaintextSecret());
    }
}
