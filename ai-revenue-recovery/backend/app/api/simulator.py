from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.cases import get_ai_client, get_payment_client
from app.ai.gemini_client import GeminiRecoveryClient
from app.clients.payment_system_client import PaymentSystemClient, MockPaymentSystemClient
from app.clients.real_payment_system_client import RealPaymentSystemClient
from app.config import get_settings
from app.db import get_db
from app.models.recovery import FailureType, RevenueSourceType
from app.schemas.recovery import CaseDetailOut
from app.services.recovery_service import RecoveryService

router = APIRouter(prefix="/api/simulator", tags=["simulator"])


class PaymentFailureRequest(BaseModel):
    failure_type: FailureType
    amount: float = 4999.0
    provider: str | None = "MOCK_PROVIDER_A"
    auto_run: bool = True


class ProviderHealthRequest(BaseModel):
    provider: str
    health: str  # HEALTHY | DEGRADED | DOWN


@router.post("/payment-failure", response_model=CaseDetailOut)
async def simulate_payment_failure(
    body: PaymentFailureRequest,
    db: Session = Depends(get_db),
    payment_client: PaymentSystemClient = Depends(get_payment_client),
    ai_client: GeminiRecoveryClient = Depends(get_ai_client),
):
    ctx = await payment_client.simulate_payment_failure(body.failure_type, body.amount, body.provider)

    service = RecoveryService(db=db, payment_client=payment_client, ai_client=ai_client)
    case, _ = service.detect(
        external_reference=ctx["external_reference"],
        source_type=RevenueSourceType.ONE_OFF_PAYMENT,
        amount=ctx["amount"],
        currency=ctx["currency"],
        failure_type=body.failure_type,
        failure_reason=ctx["failure_reason"],
        provider=ctx["provider"],
    )

    if body.auto_run:
        provider_health = payment_client.get_provider_health_snapshot()
        await service.analyze(case, provider_health=provider_health)
        db.refresh(case)
        if case.plans:
            await service.validate_and_execute_next(case, provider_health=provider_health)
            db.refresh(case)

    return case


@router.post("/missed-collection", response_model=CaseDetailOut)
async def simulate_missed_collection(
    amount: float = 999.0,
    customer_ref: str = "cust_demo",
    db: Session = Depends(get_db),
    payment_client: PaymentSystemClient = Depends(get_payment_client),
    ai_client: GeminiRecoveryClient = Depends(get_ai_client),
):
    ctx = await payment_client.simulate_payment_failure(FailureType.MISSED_COLLECTION, amount, None)

    service = RecoveryService(db=db, payment_client=payment_client, ai_client=ai_client)
    case, _ = service.detect(
        external_reference=ctx["external_reference"],
        source_type=RevenueSourceType.RECURRING_COLLECTION,
        amount=amount,
        currency="INR",
        failure_type=FailureType.MISSED_COLLECTION,
        failure_reason="No successful attempt by expected collection date",
        provider=None,
    )
    provider_health = payment_client.get_provider_health_snapshot()
    await service.analyze(case, provider_health=provider_health)
    db.refresh(case)
    return case


@router.post("/provider-health")
def set_provider_health(
    body: ProviderHealthRequest,
    payment_client: PaymentSystemClient = Depends(get_payment_client),
):
    payment_client.set_provider_health(body.provider, body.health)
    return {"provider": body.provider, "health": body.health}


