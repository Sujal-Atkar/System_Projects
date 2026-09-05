package com.system_project.distributedpaymentsystem.api.internal;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryContextResponse;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteRequest;
import com.system_project.distributedpaymentsystem.api.internal.dto.RecoveryExecuteResponse;
import com.system_project.distributedpaymentsystem.service.recovery.InternalRecoveryExecutionService;

@RestController
@RequestMapping("/api/internal/payments")
public class InternalRecoveryController {

    private final InternalRecoveryExecutionService executionService;
    private final String internalSecret;

    public InternalRecoveryController(
            InternalRecoveryExecutionService executionService,
            @Value("${payment.recovery.internal-secret}") String internalSecret) {
        this.executionService = executionService;
        this.internalSecret = internalSecret;
    }

    private void authenticate(String secretHeader) {
        if (secretHeader == null || !internalSecret.equals(secretHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal secret");
        }
    }

    @GetMapping("/{paymentId}/recovery-context")
    public RecoveryContextResponse getContext(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        authenticate(secret);
        return executionService.getContext(paymentId);
    }

    @PostMapping("/{paymentId}/recovery-execute")
    public RecoveryExecuteResponse executeRecovery(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody RecoveryExecuteRequest request) {
        authenticate(secret);
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        }
        return executionService.executeRecovery(paymentId, idempotencyKey, request);
    }
}
