#!/usr/bin/env python3
"""
Prints a reproducible evaluation report from whatever batch is currently in
the database. Run scripts/generate_batch.py first.

    python scripts/run_evaluation.py
    python scripts/run_evaluation.py --json report.json
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend"))

from app.db import SessionLocal  # noqa: E402
from app.services.analytics_service import compute_summary  # noqa: E402


def render_report(summary) -> str:
    lines = []
    lines.append("=" * 60)
    lines.append("AI REVENUE RECOVERY — BATCH EVALUATION REPORT")
    lines.append("=" * 60)
    lines.append(f"Batch size:              {summary.batch_size}")
    lines.append(f"Revenue at risk:         Rs {summary.revenue_at_risk:,.2f}")
    lines.append("")
    lines.append("-- 3-way comparison (same batch, same assumptions) --")
    lines.append(f"  Do nothing:            Rs {summary.baseline_do_nothing:,.2f}")
    lines.append(f"  Blind retry (naive):   Rs {summary.baseline_blind_retry:,.2f}")
    lines.append(f"  AI recovery (actual):  Rs {summary.ai_recovered:,.2f}")
    if summary.baseline_blind_retry > 0:
        lift = (summary.ai_recovered - summary.baseline_blind_retry) / summary.baseline_blind_retry * 100
        lines.append(f"  Lift over blind retry: {lift:+.1f}%")
    lines.append("")
    lines.append(f"Recovery rate:           {summary.recovery_rate * 100:.1f}%")
    lines.append(f"Intervention cost total: Rs {summary.intervention_cost_total:,.2f}")
    lines.append(f"Net recovered revenue:   Rs {summary.net_recovered_revenue:,.2f}")
    lines.append("")
    lines.append("Failure type breakdown:")
    for k, v in sorted(summary.failure_type_breakdown.items(), key=lambda kv: -kv[1]):
        lines.append(f"  {k:<30} {v}")
    lines.append("")
    lines.append("SIMULATION ASSUMPTIONS")
    lines.append("  - Blind-retry baseline uses a flat 35% assumed success rate,")
    lines.append("    applied uniformly to every non-business-failure case. This is")
    lines.append("    a naive comparison policy, not a measurement of a real system.")
    lines.append("  - 'Do nothing' is exactly Rs 0 by definition.")
    lines.append("  - AI recovery numbers reflect actual outcomes from")
    lines.append("    MockPaymentSystemClient's seeded simulation, not fabricated.")
    lines.append("  - Provider success rates, and any case run without")
    lines.append("    GEMINI_API_KEY configured, use the deterministic policy")
    lines.append("    fallback rather than a live model call — see the per-case")
    lines.append("    decision_source field to distinguish AI from fallback.")
    lines.append("=" * 60)
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=str, default=None, help="Also write the raw summary as JSON")
    args = parser.parse_args()

    db = SessionLocal()
    summary = compute_summary(db)
    db.close()

    print(render_report(summary))

    if args.json:
        with open(args.json, "w") as f:
            json.dump(summary.model_dump(), f, indent=2)
        print(f"\nRaw summary written to {args.json}")


if __name__ == "__main__":
    main()
