from __future__ import annotations

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.db import Base, get_db
from app.main import app
from app.api.cases import get_payment_client
from app.clients.payment_system_client import MockPaymentSystemClient
from app.models.recovery import RecoveryCase, CaseStatus


@pytest.fixture
def test_engine():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    yield engine
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def TestingSessionLocal(test_engine):
    return sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


@pytest.fixture
def client(test_engine, TestingSessionLocal):
    def override_get_db():
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    mock_payment = MockPaymentSystemClient(seed=123)
    app.dependency_overrides[get_payment_client] = lambda: mock_payment
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


def test_webhook_returns_202_and_creates_case(client, TestingSessionLocal):
    mock_payment = app.dependency_overrides[get_payment_client]()
    mock_payment._contexts["pay_test_001"] = {
        "amount": 2500.0,
        "currency": "INR",
        "provider": "MOCK_PROVIDER_A",
        "failure_type": "TRANSIENT_PROVIDER_FAILURE",
    }
    payload = {
        "paymentId": "pay_test_001",
        "amount": 2500.0,
        "currency": "INR",
        "errorCode": "GATEWAY_TIMEOUT",
        "gateway": "MOCK_PROVIDER_A",
        "failureReason": "Gateway timed out during processing",
    }
    response = client.post("/api/recovery/detect", json=payload)
    assert response.status_code == 202
    data = response.json()
    assert data["status"] == "ACCEPTED"
    assert data["is_new_case"] is True
    assert data["external_reference"] == "pay_test_001"

    with TestingSessionLocal() as session:
        case = session.query(RecoveryCase).filter_by(external_reference="pay_test_001").first()
        assert case is not None
        assert float(case.amount) == 2500.0


def test_webhook_malformed_payload_fails_validation(client):
    response = client.post("/api/recovery/detect", json={"currency": "INR"})
    assert response.status_code == 422


def test_duplicate_webhook_does_not_create_duplicate_case(client, TestingSessionLocal):
    payload = {
        "paymentId": "pay_duplicate_001",
        "amount": 1000.0,
        "currency": "INR",
        "errorCode": "INSUFFICIENT_FUNDS",
        "gateway": "MOCK_PROVIDER_A",
    }

    # First delivery
    res1 = client.post("/api/recovery/detect", json=payload)
    assert res1.status_code == 202
    data1 = res1.json()
    assert data1["is_new_case"] is True
    first_case_id = data1["case_id"]

    # Duplicate delivery
    res2 = client.post("/api/recovery/detect", json=payload)
    assert res2.status_code == 202
    data2 = res2.json()
    assert data2["is_new_case"] is False
    assert data2["case_id"] == first_case_id

    with TestingSessionLocal() as session:
        cases = session.query(RecoveryCase).filter_by(external_reference="pay_duplicate_001").all()
        assert len(cases) == 1


def test_webhook_background_execution_runs(client, TestingSessionLocal):
    mock_payment = app.dependency_overrides[get_payment_client]()
    mock_payment._contexts["pay_bg_001"] = {
        "amount": 5000.0,
        "currency": "INR",
        "provider": "MOCK_PROVIDER_A",
        "failure_type": "TRANSIENT_PROVIDER_FAILURE",
    }
    payload = {
        "paymentId": "pay_bg_001",
        "amount": 5000.0,
        "currency": "INR",
        "errorCode": "GATEWAY_TIMEOUT",
        "gateway": "MOCK_PROVIDER_A",
    }
    response = client.post("/api/recovery/detect", json=payload)
    assert response.status_code == 202

    with TestingSessionLocal() as session:
        case = session.query(RecoveryCase).filter_by(external_reference="pay_bg_001").first()
        assert case is not None
        assert case.status in (CaseStatus.RECOVERED, CaseStatus.FAILED_RECOVERY, CaseStatus.STOPPED)
def test_webhook_java_context_lookup_failure_stops_recovery(client, TestingSessionLocal):
    # Testing context lookup failure when paymentId is unknown to MockPaymentSystemClient
    payload = {
        "paymentId": "pay_unknown_ctx",
        "amount": 5000.0,
        "currency": "INR",
        "errorCode": "GATEWAY_TIMEOUT",
        "gateway": "MOCK_PROVIDER_A",
    }
    response = client.post("/api/recovery/detect", json=payload)
    assert response.status_code == 202

    with TestingSessionLocal() as session:
        case = session.query(RecoveryCase).filter_by(external_reference="pay_unknown_ctx").first()
        assert case is not None
        # When context lookup fails, case is safely stopped rather than performing money movement
        assert case.status == CaseStatus.STOPPED
