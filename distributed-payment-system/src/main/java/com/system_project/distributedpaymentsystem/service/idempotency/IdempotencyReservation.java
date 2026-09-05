package com.system_project.distributedpaymentsystem.service.idempotency;

public record IdempotencyReservation(boolean reserved, boolean duplicate, String existingFingerprint) {
}
