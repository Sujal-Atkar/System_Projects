"""
Every number here is computed from RecoveryCase/RecoveryAnalysis rows that
actually exist in the database — nothing is hardcoded for the dashboard.
Run scripts/generate_batch.py first to populate a batch worth looking at.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.schemas.recovery import AnalyticsSummaryOut
from app.services.analytics_service import compute_summary

router = APIRouter(prefix="/api/analytics", tags=["analytics"])


@router.get("/summary", response_model=AnalyticsSummaryOut)
def summary(db: Session = Depends(get_db)):
    return compute_summary(db)
