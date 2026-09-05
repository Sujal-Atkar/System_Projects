from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, BackgroundTasks, Depends, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.api.cases import get_ai_client, get_payment_client
from app.ai.gemini_client import GeminiRecoveryClient
from app.clients.payment_system_client import PaymentSystemClient
from app.db import get_db
from app.models.recovery import FailureType, RevenueSourceType
from app.services.recovery_service import RecoveryService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/recovery", tags=["recovery-webhook"])


class PaymentFailureWebhookPayload(BaseModel):
    paymentId: str = Field(..., description="Authoritative payment ID from the payment platform")
    amount: float = Field(..., ge=0.0, description="Amount of the failed transaction")
    currency: str = Field(default="INR", description="Three-letter currency code")
    errorCode: str | None = Field(default=None, description="Error code reported by gateway or bank")
    gateway: str | None = Field(default=None, description="Payment gateway or provider used")
    failureReason: str | None = Field(default=None, description="Text reason or message")
    eventType: str | None = Field(default=None, description="Event type, e.g. PAYMENT_FAILED")
    status: str | None = Field(default=None, description="Failure status, e.g. FAILED")


def _map_error_code_to_failure_type(error_code: str | None) -> FailureType:
    if not error_code:
        return FailureType.OTHER
    code = error_code.upper()
    if "TIMEOUT" in code or "GATEWAY_TIMEOUT" in code or "NETWORK" in code:
        return FailureType.TRANSIENT_PROVIDER_FAILURE
    if "DEGRADED" in code or "5XX" in code:
        return FailureType.PROVIDER_DEGRADED
    if "INSUFFICIENT" in code or "BALANCE" in code:
        return FailureType.INSUFFICIENT_FUNDS
    if "EXPIRED" in code:
        return FailureType.EXPIRED_PAYMENT_METHOD
    if "INVALID" in code or "AUTH" in code or "CVV" in code:
        return FailureType.INVALID_PAYMENT_METHOD
    if "FRAUD" in code or "DECLINE" in code or "BUSINESS" in code:
        return FailureType.BUSINESS_DECLINE
    if "MISSED" in code or "COLLECTION" in code:
        return FailureType.MISSED_COLLECTION
    return FailureType.OTHER


async def _run_background_recovery(
    case_id: str,
    db: Session,
    payment_client: PaymentSystemClient,
    ai_client: GeminiRecoveryClient,
) -> None:
    try:
        service = RecoveryService(db=db, payment_client=payment_client, ai_client=ai_client)
        await service.process_background_recovery(case_id)
    except Exception as ex:
        logger.error("Error executing background recovery for case %s: %s", case_id, ex, exc_info=True)


@router.post("/detect", status_code=status.HTTP_202_ACCEPTED)
async def detect_payment_failure_webhook(
    payload: PaymentFailureWebhookPayload,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    payment_client: PaymentSystemClient = Depends(get_payment_client),
    ai_client: GeminiRecoveryClient = Depends(get_ai_client),
) -> dict[str, Any]:
    failure_type = _map_error_code_to_failure_type(payload.errorCode)

    service = RecoveryService(db=db, payment_client=payment_client, ai_client=ai_client)
    case, created = service.detect(
        external_reference=payload.paymentId,
        source_type=RevenueSourceType.ONE_OFF_PAYMENT,
        amount=payload.amount,
        currency=payload.currency,
        failure_type=failure_type,
        failure_reason=payload.failureReason or payload.errorCode,
        provider=payload.gateway,
    )

    if created:
        logger.info("Registered new RecoveryCase %s for paymentId %s", case.id, payload.paymentId)
        background_tasks.add_task(_run_background_recovery, case.id, db, payment_client, ai_client)
    else:
        logger.info(
            "Duplicate failure notification for paymentId %s. Case %s already exists; no duplicate task scheduled.",
            payload.paymentId,
            case.id,
        )

    return {
        "status": "ACCEPTED",
        "case_id": case.id,
        "is_new_case": created,
        "external_reference": payload.paymentId,
    }