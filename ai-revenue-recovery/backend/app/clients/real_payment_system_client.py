from __future__ import annotations

import httpx
import uuid
import logging
from datetime import datetime, timezone
from dataclasses import dataclass

from app.clients.payment_system_client import PaymentSystemClient, RecoveryExecutionResult
from app.models.recovery import FailureType, RecoveryActionType
from app.config import get_settings

logger = logging.getLogger(__name__)

class RealPaymentSystemClient(PaymentSystemClient):

    def __init__(self):
        self.settings = get_settings()
        self.base_url = self.settings.java_payment_api_base_url.rstrip("/")
        self.timeout = httpx.Timeout(self.settings.java_payment_api_timeout)
        self.headers = {
            "Content-Type": "application/json",
            "X-Internal-Secret": self.settings.java_payment_api_key
        }

    async def get_recovery_context(self, external_reference: str) -> dict:
        url = f"{self.base_url}/api/internal/payments/{external_reference}/recovery-context"
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.get(url, headers=self.headers)
                
                if response.status_code == 404:
                    raise KeyError(f"No known case for reference {external_reference}")
                    
                response.raise_for_status()
                data = response.json()

                # Map Java response to Python expected dict format
                return {
                    "external_reference": str(data.get("paymentId")),
                    "amount": data.get("amount", 0.0),
                    "currency": data.get("currency", "INR"),
                    "failure_type": data.get("failureType", FailureType.OTHER.value),
                    "failure_reason": data.get("failureReason", ""),
                    "provider": data.get("provider", "UNKNOWN"),
                    "provider_health": data.get("providerHealth", "HEALTHY"),
                    "attempt_number": data.get("attemptNumber", 1),
                    "previous_attempts": data.get("previousAttempts", 0),
                    "reconciliation_open": data.get("reconciliationOpen", False),
                }
            except httpx.HTTPError as e:
                logger.error(f"HTTP Error fetching recovery context for {external_reference}: {str(e)}")
                raise

    async def execute_recovery_action(
        self,
        external_reference: str,
        action: RecoveryActionType,
        idempotency_key: str,
        target_provider: str | None = None,
    ) -> RecoveryExecutionResult:
        if action in (RecoveryActionType.NOTIFY, RecoveryActionType.WAIT,
                      RecoveryActionType.REQUEST_PAYMENT_UPDATE,
                      RecoveryActionType.ESCALATE, RecoveryActionType.NO_ACTION):
            return RecoveryExecutionResult(
                success=False,
                provider_used=None,
                outcome_code="NO_MONEY_MOVEMENT",
                recovered_amount=None,
            )

        url = f"{self.base_url}/api/internal/payments/{external_reference}/recovery-execute"
        headers = dict(self.headers)
        headers["Idempotency-Key"] = idempotency_key

        payload = {
            "action": action.value,
            "targetProvider": target_provider
        }

        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.post(url, json=payload, headers=headers)

                if response.status_code == 409:
                    return RecoveryExecutionResult(
                        success=False,
                        provider_used=target_provider,
                        outcome_code="CONFLICT",
                        recovered_amount=None
                    )
                elif response.status_code == 404:
                    return RecoveryExecutionResult(
                        success=False,
                        provider_used=target_provider,
                        outcome_code="PAYMENT_NOT_FOUND",
                        recovered_amount=None
                    )

                response.raise_for_status()
                data = response.json()

                return RecoveryExecutionResult(
                    success=data.get("success", False),
                    provider_used=data.get("providerUsed"),
                    outcome_code=data.get("outcomeCode", "UNKNOWN"),
                    recovered_amount=data.get("recoveredAmount"),
                    executed_at=datetime.now(timezone.utc)
                )

            except httpx.TimeoutException:
                logger.error(f"Timeout executing recovery action for {external_reference}")
                return RecoveryExecutionResult(
                    success=False,
                    provider_used=target_provider,
                    outcome_code="TIMEOUT",
                    recovered_amount=None
                )
            except httpx.HTTPError as e:
                logger.error(f"Network error executing recovery action for {external_reference}: {str(e)}")
                return RecoveryExecutionResult(
                    success=False,
                    provider_used=target_provider,
                    outcome_code="NETWORK_ERROR",
                    recovered_amount=None
                )

    def set_provider_health(self, provider: str, health: str) -> None:
        raise NotImplementedError("Provider health override is only for MockPaymentSystemClient")

    def get_provider_health_snapshot(self) -> dict[str, str]:
        # Java does not yet expose a health endpoint, return mock healthy states
        return {"PROVIDER_A": "HEALTHY", "PROVIDER_B": "HEALTHY"}

    async def simulate_payment_failure(
        self, failure_type: FailureType, amount: float, provider: str | None = None
    ) -> dict:
        raise NotImplementedError("Simulating payment failures is only supported by the standalone Simulator. Use MockPaymentSystemClient.")

