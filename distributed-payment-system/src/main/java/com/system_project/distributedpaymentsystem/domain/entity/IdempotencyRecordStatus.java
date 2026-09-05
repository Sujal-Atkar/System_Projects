package com.system_project.distributedpaymentsystem.domain.entity;

public enum IdempotencyRecordStatus {
    RESERVED,
    COMPLETED,
    FAILED,
    EXPIRED
}