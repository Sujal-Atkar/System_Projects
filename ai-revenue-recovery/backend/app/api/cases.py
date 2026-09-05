from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ai.gemini_client import GeminiRecoveryClient
from app.clients.payment_system_client import PaymentSystemClient, MockPaymentSystemClient
from app.clients.real_payment_system_client import RealPaymentSystemClient
from app.config import get_settings
from app.db import get_db
from app.models.recovery import RecoveryCase
from app.schemas.recovery import CaseDetailOut, CaseSummaryOut
from app.schemas.ai import AssistantRequest, AssistantResponse
from app.services.recovery_service import RecoveryService

router = APIRouter(prefix="/api/recovery/cases", tags=["cases"])

settings = get_settings()
if settings.use_real_payment_system:
    _payment_client = RealPaymentSystemClient()
else:
    _payment_client = MockPaymentSystemClient(seed=42)
_ai_client = GeminiRecoveryClient()


def get_payment_client() -> PaymentSystemClient:
    return _payment_client


def get_ai_client() -> GeminiRecoveryClient:
    return _ai_client


def get_recovery_service(
    db: Session = Depends(get_db),
    payment_client: PaymentSystemClient = Depends(get_payment_client),
    ai_client: GeminiRecoveryClient = Depends(get_ai_client),
) -> RecoveryService:
    return RecoveryService(db=db, payment_client=payment_client, ai_client=ai_client)


@router.get("", response_model=list[CaseSummaryOut])
def list_cases(db: Session = Depends(get_db), limit: int = 50):
    cases = db.execute(
        select(RecoveryCase).order_by(RecoveryCase.created_at.desc()).limit(limit)
    ).scalars().all()
    return cases


@router.get("/{case_id}", response_model=CaseDetailOut)
def get_case(case_id: str, db: Session = Depends(get_db)):
    case = db.get(RecoveryCase, case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="Case not found")
    return case


@router.post("/{case_id}/assistant", response_model=AssistantResponse)
async def explain_case(
    case_id: str,
    request: AssistantRequest,
    db: Session = Depends(get_db),
    ai_client: GeminiRecoveryClient = Depends(get_ai_client),
):
    case = db.get(RecoveryCase, case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="Case not found")

    analysis = case.analyses[-1] if case.analyses else None
    context: dict[str, object] = {
        "case_id": case.id,
        "status": case.status.value,
        "failure_type": case.failure_type.value,
        "failure_reason": case.failure_reason,
        "amount": float(case.amount),
        "currency": case.currency,
        "provider": case.provider,
        "attempt_number": case.attempt_number,
        "recovered_amount": float(case.recovered_amount) if case.recovered_amount is not None else None,
        "diagnosis": analysis.diagnosis if analysis else None,
        "confidence_estimate": float(analysis.confidence) if analysis else None,
        "recovery_probability_estimate": float(analysis.recovery_probability) if analysis else None,
        "organic_recovery_probability_estimate": float(analysis.organic_recovery_probability) if analysis else None,
        "expected_recovery_estimate": float(analysis.expected_incremental_value) if analysis else None,
        "intervention_cost_estimate": float(analysis.intervention_cost) if analysis else None,
        "expected_net_value_estimate": float(analysis.expected_net_value) if analysis else None,
        "decision_source": analysis.decision_source.value if analysis else None,
    }
    result = await ai_client.explain_case(question=request.message, context=context)
    if result.succeeded and result.answer:
        return AssistantResponse(answer=result.answer, source="AI", case_id=case.id)

    recovered = context["recovered_amount"]
    fallback = (
        f"Verified case data shows status {case.status.value.replace('_', ' ').lower()} for "
        f"{case.currency} {float(case.amount):,.0f} at risk. "
        + (f"The authoritative recovered amount is {case.currency} {recovered:,.0f}." if recovered is not None
           else "An AI explanation is temporarily unavailable; no recovery result is being inferred.")
    )
    return AssistantResponse(answer=fallback, source="FALLBACK", case_id=case.id)


@router.post("/{case_id}/analyze", response_model=CaseDetailOut)
async def analyze_case(
    case_id: str,
    db: Session = Depends(get_db),
    service: RecoveryService = Depends(get_recovery_service),
    payment_client: PaymentSystemClient = Depends(get_payment_client),
):
    case = db.get(RecoveryCase, case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="Case not found")
    provider_health = payment_client.get_provider_health_snapshot()
    await service.analyze(case, provider_health=provider_health)
    db.refresh(case)
    return case


@router.post("/{case_id}/execute-next", response_model=CaseDetailOut)
async def execute_next(
    case_id: str,
    db: Session = Depends(get_db),
    service: RecoveryService = Depends(get_recovery_service),
    payment_client: PaymentSystemClient = Depends(get_payment_client),
):
    case = db.get(RecoveryCase, case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="Case not found")
    provider_health = payment_client.get_provider_health_snapshot()
    await service.validate_and_execute_next(case, provider_health=provider_health)
    db.refresh(case)
    return case

