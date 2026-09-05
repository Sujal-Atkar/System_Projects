"""
The integration boundary to the (not-yet-connected) Java payment platform.
"""
from __future__ import annotations

import random
import uuid
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timezone

from app.models.recovery import FailureType, RecoveryActionType

@dataclass
class RecoveryExecutionResult:
    success: bool
    provider_used: str | None
    outcome_code: str
    recovered_amount: float | None
    executed_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))


class PaymentSystemClient(ABC):
    @abstractmethod
    async def get_recovery_context(self, external_reference: str) -> dict:
        pass

    @abstractmethod
    async def execute_recovery_action(
        self,
        external_reference: str,
        action: RecoveryActionType,
        idempotency_key: str,
        target_provider: str | None = None,
    ) -> RecoveryExecutionResult:
        pass
        
    @abstractmethod
    def set_provider_health(self, provider: str, health: str) -> None:
        pass

    @abstractmethod
    def get_provider_health_snapshot(self) -> dict[str, str]:
        pass

    @abstractmethod
    async def simulate_payment_failure(
        self, failure_type: FailureType, amount: float, provider: str | None = None
    ) -> dict:
        pass


class MockPaymentSystemClient(PaymentSystemClient):
    def __init__(self, seed: int | None = None) -> None:
        self._rng = random.Random(seed)
        self._provider_health: dict[str, str] = {
            "MOCK_PROVIDER_A": "HEALTHY",
            "MOCK_PROVIDER_B": "HEALTHY",
        }
        self._contexts: dict[str, dict] = {}

    def set_provider_health(self, provider: str, health: str) -> None:
        self._provider_health[provider] = health

    def get_provider_health_snapshot(self) -> dict[str, str]:
        return dict(self._provider_health)

    async def get_recovery_context(self, external_reference: str) -> dict:
        ctx = self._contexts.get(external_reference)
        if ctx is None:
            raise KeyError(f"No known case for reference {external_reference}")
        return ctx

    async def execute_recovery_action(
        self,
        external_reference: str,
        action: RecoveryActionType,
        idempotency_key: str,
        target_provider: str | None = None,
    ) -> RecoveryExecutionResult:
        ctx = self._contexts.get(external_reference, {})
        amount = ctx.get("amount", 0.0)

        if action in (RecoveryActionType.NOTIFY, RecoveryActionType.WAIT,
                      RecoveryActionType.REQUEST_PAYMENT_UPDATE,
                      RecoveryActionType.ESCALATE, RecoveryActionType.NO_ACTION):
            return RecoveryExecutionResult(
                success=False,
                provider_used=None,
                outcome_code="NO_MONEY_MOVEMENT",
                recovered_amount=None,
            )

        provider = target_provider or ctx.get("provider") or "MOCK_PROVIDER_A"
        health = self._provider_health.get(provider, "HEALTHY")

        if health == "DOWN":
            return RecoveryExecutionResult(
                success=False, provider_used=provider,
                outcome_code="PROVIDER_UNAVAILABLE", recovered_amount=None,
            )

        base_success_rate = 0.78 if health == "HEALTHY" else 0.45
        if ctx.get("failure_type") in (
            FailureType.INSUFFICIENT_FUNDS.value,
            FailureType.BUSINESS_DECLINE.value,
        ):
            base_success_rate = 0.12

        success = self._rng.random() < base_success_rate
        return RecoveryExecutionResult(
            success=success,
            provider_used=provider,
            outcome_code="SUCCESS" if success else "RETRY_FAILED",
            recovered_amount=amount if success else None,
        )

    async def simulate_payment_failure(
        self, failure_type: FailureType, amount: float, provider: str | None = None
    ) -> dict:
        ref = f"sim_{uuid.uuid4().hex[:12]}"
        provider = provider or "MOCK_PROVIDER_A"
        ctx = {
            "external_reference": ref,
            "amount": amount,
            "currency": "INR",
            "failure_type": failure_type.value,
            "failure_reason": _reason_for(failure_type),
            "provider": provider,
            "provider_health": self._provider_health.get(provider, "HEALTHY"),
            "attempt_number": 1,
            "previous_attempts": 0,
        }
        self._contexts[ref] = ctx
        return ctx


def _reason_for(failure_type: FailureType) -> str:
    return {
        FailureType.TRANSIENT_PROVIDER_FAILURE: "Gateway timeout after 15000ms",
        FailureType.PROVIDER_DEGRADED: "Elevated 5xx rate on issuer switch",
        FailureType.INSUFFICIENT_FUNDS: "Issuer declined: insufficient funds",
        FailureType.EXPIRED_PAYMENT_METHOD: "Card expired",
        FailureType.INVALID_PAYMENT_METHOD: "Invalid CVV / authentication failed",
        FailureType.BUSINESS_DECLINE: "Issuer declined: fraud suspected",
        FailureType.MISSED_COLLECTION: "No successful attempt by expected collection date",
        FailureType.OTHER: "Unclassified failure",
    }.get(failure_type, "Unclassified failure")
