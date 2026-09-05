#!/usr/bin/env python3
"""
Runs a reproducible batch of failure scenarios through the real recovery
pipeline (same policy, economics, and — if GEMINI_API_KEY is set — the same
AI decisioning the live product uses). This is what makes the batch
analytics numbers real rather than fabricated: every row in the summary
came from an actual RecoveryCase that went through detect -> analyze ->
validate -> execute -> observe.

Usage:
    python scripts/generate_batch.py --size 1000
    python scripts/generate_batch.py --size 100 --use-ai   # real Gemini calls
    python scripts/generate_batch.py --size 1000 --reset   # wipe DB first

Without --use-ai (the default), every case goes through the deterministic
fallback path — fast, free, and enough to prove the pipeline and the
economic-floor / business-failure / provider-health gates work at volume.
Use --use-ai on a smaller batch to demonstrate real model decisions; a
1000-case run against a live API is neither fast nor necessary to prove
the point.
"""
from __future__ import annotations

import argparse
import asyncio
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend"))

from app.ai.gemini_client import GeminiRecoveryClient  # noqa: E402
from app.clients.payment_system_client import MockPaymentSystemClient  # noqa: E402
from app.db import SessionLocal, init_db, engine  # noqa: E402
from app.models.recovery import Base  # noqa: E402
from app.services.recovery_service import RecoveryService  # noqa: E402
from app.simulation.scenarios import generate_batch  # noqa: E402


async def run(size: int, seed: int, use_ai: bool, reset: bool) -> None:
    if reset:
        print("Resetting database...")
        Base.metadata.drop_all(bind=engine)
    init_db()

    if not use_ai:
        os.environ.pop("GEMINI_API_KEY", None)
        print("Running in deterministic-only mode (no --use-ai). "
              "Every case will use the policy fallback, not a live model call.")
    elif not os.environ.get("GEMINI_API_KEY"):
        print("WARNING: --use-ai was set but GEMINI_API_KEY is not configured. "
              "Falling back to deterministic decisions for all cases.")

    db = SessionLocal()
    payment_client = MockPaymentSystemClient(seed=seed)
    ai_client = GeminiRecoveryClient()
    service = RecoveryService(db=db, payment_client=payment_client, ai_client=ai_client)

    scenarios = generate_batch(size=size, seed=seed)
    started = time.monotonic()

    for i, scenario in enumerate(scenarios, start=1):
        ctx = await payment_client.simulate_payment_failure(
            scenario.failure_type, scenario.amount, scenario.provider
        )
        case = service.detect(
            external_reference=ctx["external_reference"],
            source_type=scenario.source_type,
            amount=ctx["amount"],
            currency="INR",
            failure_type=scenario.failure_type,
            failure_reason=ctx["failure_reason"],
            provider=ctx["provider"],
        )
        provider_health = payment_client.get_provider_health_snapshot()
        await service.analyze(case, provider_health=provider_health)
        if case.plans:
            await service.validate_and_execute_next(case, provider_health=provider_health)

        if i % 100 == 0 or i == size:
            elapsed = time.monotonic() - started
            print(f"  {i}/{size} cases processed ({elapsed:.1f}s elapsed)")

    db.close()
    print(f"\nBatch complete: {size} cases, seed={seed}, use_ai={use_ai}.")
    print("Run scripts/run_evaluation.py to see the summary report.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--size", type=int, default=1000, help="Number of cases to generate")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for reproducibility")
    parser.add_argument("--use-ai", action="store_true", help="Call the real Gemini API for each case")
    parser.add_argument("--reset", action="store_true", help="Drop and recreate all tables first")
    args = parser.parse_args()

    asyncio.run(run(size=args.size, seed=args.seed, use_ai=args.use_ai, reset=args.reset))


if __name__ == "__main__":
    main()
