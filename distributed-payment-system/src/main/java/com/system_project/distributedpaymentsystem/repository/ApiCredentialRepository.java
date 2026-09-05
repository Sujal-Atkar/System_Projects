package com.system_project.distributedpaymentsystem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.ApiCredential;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {
    Optional<ApiCredential> findByKeyId(String keyId);
}
