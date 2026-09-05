# AI Revenue Recovery Platform

An AI-powered revenue recovery system that detects failed or missed payments, evaluates whether recovery is economically worthwhile, selects a bounded recovery strategy, executes the approved action through a payment system, and measures the money actually recovered.

This repository contains two cooperating systems:

- `ai-revenue-recovery/` — revenue intelligence, AI analysis, recovery planning, economics, simulation, analytics, and operator UI
- `distributed-payment-system/` — payment execution, provider routing, idempotency, reconciliation, ledger, and financial authority

The core idea is simple:

> **Detect → Decide → Act → Recover → Measure**

---

## Problem

A failed payment does not always mean permanently lost revenue.

Payments can fail because of:

- transient gateway/provider failures
- degraded payment providers
- insufficient funds
- expired or invalid payment methods
- missed recurring collections
- economically unattractive recovery opportunities

A blind retry treats every failure the same.

This project instead asks:

1. Is this payment actually recoverable?
2. Is intervention economically worthwhile?
3. What recovery strategy should be attempted?
4. Can that action be executed safely?
5. Did the intervention actually recover revenue?

---

# Product

The platform acts as a recovery intelligence layer above a payment system.

It combines:

- AI-based failure diagnosis and recovery recommendation
- deterministic economic evaluation
- deterministic safety and policy gates
- bounded recovery plans
- payment-system execution
- reconciliation and ledger verification
- recovery analytics
- operator-facing recovery cases
- an interactive failure simulator

The AI proposes and explains.

The payment system executes.

The ledger remains the financial source of truth.

---

# Architecture

```mermaid
flowchart LR

    A[Customer Payment] --> B[Distributed Payment System]

    B --> C[Payment Orchestrator]
    C --> D[Idempotency]
    D --> E[Provider Router]
    E --> F[Payment Provider]

    F --> G{Payment Result}

    G -->|SUCCESS| H[Ledger]
    G -->|FAILED / TIMEOUT| I[Revenue Risk]

    I --> J[Kafka Payment Event]

    J --> K[Python Revenue Recovery Platform]

    K --> L[Failure Analysis]
    L --> M[Gemini AI]
    L --> N[Deterministic Economics]
    L --> O[Recovery Policy]

    M --> P[Recovery Plan]
    N --> P
    O --> P

    P --> Q{Intervention Worthwhile?}

    Q -->|NO| R[NO ACTION]
    Q -->|YES| S[Approved Recovery Action]

    S --> T[Java Recovery Execution API]

    T --> U[Payment Service]
    U --> V[Provider Router]
    V --> W[Provider]

    W --> X{Recovery Result}

    X -->|SUCCESS| H
    X -->|FAILED| Y[Recovery Result / Case]

    H --> Z[Ledger / Financial Truth]

    K --> AA[Recovery Dashboard]
    K --> AB[Recovery Case]
    K --> AC[Simulator]
    K --> AD[Analytics]