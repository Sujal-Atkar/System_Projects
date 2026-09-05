from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import analytics, cases, simulator, webhooks
from app.db import init_db

app = FastAPI(
    title="AI Revenue Recovery Platform",
    description="Standalone recovery-intelligence product. Talks to a mock "
    "payment system today; the PaymentSystemClient boundary is where the "
    "real Java platform plugs in later without touching anything else here.",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def on_startup() -> None:
    init_db()


app.include_router(cases.router)
app.include_router(simulator.router)
app.include_router(analytics.router)
app.include_router(webhooks.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
