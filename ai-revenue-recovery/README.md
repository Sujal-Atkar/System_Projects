# AI Revenue Recovery Platform

A standalone product built for the Razorpay AI Buildathon — AI Revenue Recovery track.
Detects revenue at risk from failed payments and missed collections, gets an AI-backed
recommendation on the right intervention, executes exactly one validated step at a time,
and proves what was actually recovered — never fabricated dashboard numbers.

This is a separate project from the existing Java distributed payment system. It talks to
a mock payment client today; the integration boundary (`PaymentSystemClient`) is where the
real Java REST APIs plug in later without touching anything else in this codebase.

## The principle this whole system is built around

```
AI proposes -> Economics evaluate -> Rules constrain -> The financial system executes -> The outcome proves
```

The LLM never calls a payment provider, never decides "is this worth it" by itself, and
never gets the final say over what executes. It chooses one action from a set the
deterministic policy layer already computed as legal; the policy layer re-validates that
choice immediately before execution; the economics formula (not model confidence) decides
whether intervening is worth the cost; and every recovered rupee is read back from an
actual executed step, not asserted by the AI.

## Architecture

```
Simulator / (future) real payment webhook
        |
        v
   DETECT  ->  RecoveryCase created
        |
        v
  ANALYZE  ->  RecoveryPolicy computes allowed actions (deterministic gates:
        |      reconciliation-open, attempt cap, business-failure, economic
        |      floor, provider health)
        |         |
        |         v
        |      Gemini (structured JSON output) chooses from the allowed set,
        |      OR deterministic fallback if AI is unavailable/times out/
        |      returns something invalid — every failure mode degrades
        |      cleanly, never blocks the pipeline
        |         |
        |         v
        |      Economics formula (expected_net_value) gates whether it's
        |      worth intervening at all, independent of what AI recommended
        |
        v
    PLAN    ->  RecoveryPlan + steps persisted
        |
        v
 VALIDATE  ->  Re-checked against policy *right now*, not against the set
        |      computed when analyze() ran — state may have changed
        |
        v
 EXECUTE ONE STEP  ->  PaymentSystemClient.execute_recovery_action()
        |               (only place money can move)
        v
  OBSERVE  ->  RECOVERED / FAILED_RECOVERY / STOPPED
```

## Project structure

```
ai-revenue-recovery/
├── backend/            FastAPI + SQLAlchemy + Pydantic
│   ├── app/
│   │   ├── models/       SQLAlchemy domain models (RecoveryCase, RecoveryAnalysis,
│   │   │                 RecoveryPlan, RecoveryPlanStep, ScheduledCollection)
│   │   ├── schemas/      Pydantic contracts, incl. the strict AI input/output shapes
│   │   ├── clients/      PaymentSystemClient abstraction + MockPaymentSystemClient
│   │   ├── ai/           Gemini structured-output client with hard timeout + fallback
│   │   ├── policies/     Deterministic safety gates + strategy catalogue
│   │   ├── services/     Economics evaluator + RecoveryService orchestrator
│   │   ├── simulation/   Deterministic batch scenario generator
│   │   └── api/          FastAPI routers (cases, simulator, analytics)
│   └── tests/          21 tests: economics, policy gates, AI failure modes, full E2E flow
├── frontend/           Next.js 15 + TypeScript + Tailwind
│   └── app/             Overview, Cases, Case Detail, Simulator, Batch Analytics
├── scripts/
│   ├── generate_batch.py   Pushes N scenarios through the real pipeline, reproducibly
│   └── run_evaluation.py   Prints the 3-way (do-nothing / blind-retry / AI) comparison
└── docker-compose.yml   Postgres only — a separate database from the Java system's
```

## Quick start

### 1. Database (optional — SQLite works fine for local dev)

```bash
docker compose up -d postgres
```

Or just leave `DATABASE_URL` at its SQLite default in `.env` — no Docker needed.

### 2. Backend

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # add GEMINI_API_KEY if you want real AI decisions
uvicorn app.main:app --reload --port 8000
```

Visit `http://localhost:8000/docs` for the interactive API explorer.

### 3. Frontend

```bash
cd frontend
npm install
cp .env.example .env.local  # only needed if the backend isn't on localhost:8000
npm run dev
```

Visit `http://localhost:3000`.

### 4. Try it

Open the Simulator page and click a scenario — you'll be redirected straight to the
generated case, showing diagnosis, economics, the AI's reasoning, the recovery plan, and
the execution outcome. Toggle "Provider A health" to DOWN first and re-run a scenario to
watch it reroute to Provider B instead of retrying blind.

### 5. Batch evaluation (the "measured across a batch" requirement)

```bash
cd backend
python ../scripts/generate_batch.py --size 1000     # deterministic, no API key needed
python ../scripts/run_evaluation.py                 # prints the 3-way comparison report
```

Add `--use-ai` on a smaller batch (e.g. `--size 100 --use-ai`) to run real Gemini calls
instead of the deterministic fallback — useful for a real-AI-decisions demo without the
latency/cost of doing that at 1000-case scale.

Then open `/analytics` in the frontend to see the same numbers rendered.

## Running tests

```bash
cd backend
pytest -q
```

21 tests, all passing: economic formula edge cases, every deterministic policy gate
(reconciliation-open, attempt cap, business failure, economic floor, provider health),
every Gemini failure mode (timeout, malformed response, disallowed action, missing key),
and full end-to-end recovery flows including a deliberately staged race between decision
and execution (state changes after the plan is proposed, before it's validated).

## The Java integration boundary

Nothing in this codebase imports a concrete payment client — every service depends on the
abstract `PaymentSystemClient` (`backend/app/clients/payment_system_client.py`), and only
`MockPaymentSystemClient` exists today. When the real Java platform is ready to connect:

1. Write `RealPaymentSystemClient(PaymentSystemClient)` calling the actual Spring Boot
   REST endpoints (base URL, key, and secret are already read from `.env` —
   `JAVA_PAYMENT_API_*` — unused until this client exists).
2. Swap the dependency wiring in `app/api/cases.py` / `app/api/simulator.py`.

No other file changes. The AI decisioning, policy gates, economics, and every page in the
frontend are written against the abstraction, not the mock's specific behavior.

## Implementation report

**Fully implemented and verified (build + tests + live smoke tests):**
- Domain models, deterministic policy layer (6 gates), economics evaluator, Gemini
  structured-output client with full fallback matrix, `RecoveryService` orchestrator
- Mock payment client with seeded, reproducible provider behavior
- All backend API routes (cases, simulator, analytics) — verified live over HTTP, not
  just at import time
- All 5 frontend pages, verified rendering against a live backend with real data
- Batch simulation + evaluation scripts, verified reproducible across identical seeds
- 21/21 backend tests passing

**Explicitly out of scope for this build** (per spec, and consistent with the existing
Java system's own architecture decision to keep AI decisioning in-process rather than
splitting into a separate service):
- `RealPaymentSystemClient` — the abstraction is ready; not implemented, as instructed
- WhatsApp/SMS/voice channels, full CRM, subscription billing engine, workflow editor,
  Kubernetes, multi-agent orchestration — none of these were needed to satisfy the track's
  bar, and building them would have cost days for no bar-relevant payoff

**Known limitations, documented rather than hidden:**
- `next/font/google` requires real internet access at build/dev time (fails gracefully to
  system fonts if unavailable — verified this degrades cleanly rather than crashing)
- A transitive PostCSS advisory ships inside Next.js's own dependency tree; the only fix
  is a breaking Next 16 upgrade, and the advisory concerns processing untrusted CSS input,
  which doesn't apply here (all CSS is authored, not user-supplied)
- Single-node only — no distributed state, matching the hackathon's actual scale
  requirements and mirroring the same trade-off already made in the Java system's circuit
  breaker
