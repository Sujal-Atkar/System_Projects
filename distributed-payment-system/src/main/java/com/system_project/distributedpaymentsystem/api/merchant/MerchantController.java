package com.system_project.distributedpaymentsystem.api.merchant;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.system_project.distributedpaymentsystem.api.auth.AuthenticatedMerchantContext;
import com.system_project.distributedpaymentsystem.api.merchant.dto.CreateMerchantRequest;
// import com.system_project.distributedpaymentsystem.api.merchant.dto.CreateMerchantResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.GetMerchantResponse;
import com.system_project.distributedpaymentsystem.api.merchant.dto.MerchantOnboardingResponse;
import com.system_project.distributedpaymentsystem.exception.ForbiddenAccessException;
import com.system_project.distributedpaymentsystem.service.merchant.MerchantService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantOnboardingResponse createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return merchantService.createMerchantWithCredential(request);
    }

    @GetMapping("/merchants/{merchantId}")
    public GetMerchantResponse getMerchant(@PathVariable UUID merchantId, HttpServletRequest httpServletRequest) {
        UUID authenticatedMerchantId = AuthenticatedMerchantContext.getAuthenticatedMerchantId(httpServletRequest);
        if (!merchantId.equals(authenticatedMerchantId)) {
            throw new ForbiddenAccessException(authenticatedMerchantId);
        }
        return merchantService.getMerchant(merchantId);
    }
}
