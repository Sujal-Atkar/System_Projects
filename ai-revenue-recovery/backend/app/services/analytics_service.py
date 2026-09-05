from __future__ import annotations

from collections import Counter

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.recovery import CaseStatus, RecoveryAnalysis, RecoveryCase
from app.schemas.recovery import AnalyticsSummaryOut

FLAT_BLIND_RETRY_SUCCESS_RATE = 0.35


def compute_summary(db: Session) -> AnalyticsSummaryOut:
    cases = db.execute(select(RecoveryCase)).scalars().all()
    analyses = db.execute(select(RecoveryAnalysis)).scalars().all()

    batch_size = len(cases)
    revenue_at_risk = sum(float(c.amount) for c in cases)
    recovered_cases = [c for c in cases if c.status == CaseStatus.RECOVERED]
    revenue_recovered = sum(float(c.recovered_amount or 0) for c in recovered_cases)
    intervention_cost_total = sum(float(a.intervention_cost) for a in analyses)

    decision_distribution: Counter[str] = Counter()
    for a in analyses:
        decision_distribution[a.diagnosis] += 1

    failure_breakdown: Counter[str] = Counter(c.failure_type.value for c in cases)

    eligible_for_blind_retry = [
        c for c in cases
        if c.failure_type.value not in ("INSUFFICIENT_FUNDS", "BUSINESS_DECLINE")
    ]
    baseline_blind_retry = sum(
        float(c.amount) * FLAT_BLIND_RETRY_SUCCESS_RATE for c in eligible_for_blind_retry
    )

    recovery_rate = (len(recovered_cases) / batch_size) if batch_size else 0.0

    return AnalyticsSummaryOut(
        batch_size=batch_size,
        revenue_at_risk=round(revenue_at_risk, 2),
        revenue_recovered=round(revenue_recovered, 2),
        recovery_rate=round(recovery_rate, 4),
        intervention_cost_total=round(intervention_cost_total, 2),
        net_recovered_revenue=round(revenue_recovered - intervention_cost_total, 2),
        decision_distribution=dict(decision_distribution),
        failure_type_breakdown=dict(failure_breakdown),
        baseline_do_nothing=0.0,
        baseline_blind_retry=round(baseline_blind_retry, 2),
        ai_recovered=round(revenue_recovered, 2),
    )
