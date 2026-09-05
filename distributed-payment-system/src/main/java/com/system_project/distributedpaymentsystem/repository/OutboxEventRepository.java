package com.system_project.distributedpaymentsystem.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.system_project.distributedpaymentsystem.domain.entity.OutboxEvent;
import com.system_project.distributedpaymentsystem.domain.entity.OutboxEventStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
            OutboxEventStatus status,
            OffsetDateTime availableAt,
            Pageable pageable);
}
