import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.ai.gemini_client import GeminiRecoveryClient
from app.clients.payment_system_client import MockPaymentSystemClient
from app.config import get_settings
from app.models.recovery import Base, CaseStatus, FailureType, RevenueSourceType
from app.services.recovery_service import RecoveryService


@pytest.fixture
def db_session():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()


@pytest.fixture(autouse=True)
def no_gemini_key(monkeypatch):
    # Forces every analyze() call in these tests down the deterministic
    # fallback path — no network access needed, and it's the path we can
    # assert on deterministically.
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


async def test_transient_failure_recovers_via_retry_fallback(db_session):
    payment_client = MockPaymentSystemClient(seed=1)
    ai_client = GeminiRecoveryClient()
    service = RecoveryService(db=db_session, payment_client=payment_client, ai_client=ai_client)

    ctx = await payment_client.simulate_payment_failure(
        FailureType.TRANSIENT_PROVIDER_FAILURE, amount=4999.0, provider="MOCK_PROVIDER_A"
    )
    case, _ = service.detect(
        external_reference=ctx["external_reference"],
        source_type=RevenueSourceType.ONE_OFF_PAYMENT,
        amount=ctx["amount"], currency="INR",
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        failure_reason=ctx["failure_reason"], provider=ctx["provider"],
    )
    assert case.status == CaseStatus.DETECTED

    provider_health = payment_client.get_provider_health_snapshot()
    analysis = await service.analyze(case, provider_health=provider_health)
    assert analysis.decision_source.value == "FALLBACK"
    assert case.status.value == "PLAN_PROPOSED"
    assert len(case.plans) == 1
    assert len(case.plans[0].steps) == 1

    result_case = await service.validate_and_execute_next(case, provider_health=provider_health)
    assert result_case.status in (CaseStatus.RECOVERED, CaseStatus.FAILED_RECOVERY)
    assert result_case.attempt_number == 2
    step = result_case.plans[-1].steps[0]
    assert step.status.value in ("SUCCESS", "FAILED")
    if result_case.status == CaseStatus.RECOVERED:
        assert result_case.recovered_amount == 4999.0


async def test_business_failure_never_reaches_retry(db_session):
    payment_client = MockPaymentSystemClient(seed=2)
    ai_client = GeminiRecoveryClient()
    service = RecoveryService(db=db_session, payment_client=payment_client, ai_client=ai_client)

    ctx = await payment_client.simulate_payment_failure(
        FailureType.INSUFFICIENT_FUNDS, amount=500.0, provider="MOCK_PROVIDER_A"
    )
    case, _ = service.detect(
        external_reference=ctx["external_reference"],
        source_type=RevenueSourceType.ONE_OFF_PAYMENT,
        amount=500.0, currency="INR",
        failure_type=FailureType.INSUFFICIENT_FUNDS,
        failure_reason=ctx["failure_reason"], provider=ctx["provider"],
    )
    provider_health = payment_client.get_provider_health_snapshot()
    await service.analyze(case, provider_health=provider_health)

    step = case.plans[-1].steps[0]
    assert step.action.value in ("NOTIFY", "NO_ACTION")

    result_case = await service.validate_and_execute_next(case, provider_health=provider_health)
    # NOTIFY never moves money -> case cannot end up RECOVERED from this step.
    assert result_case.status != CaseStatus.RECOVERED


async def test_amount_below_economic_floor_skips_llm_and_takes_no_action(db_session):
    payment_client = MockPaymentSystemClient(seed=3)
    ai_client = GeminiRecoveryClient()
    service = RecoveryService(
        db=db_session, payment_client=payment_client, ai_client=ai_client, economic_floor=100.0
    )

    ctx = await payment_client.simulate_payment_failure(
        FailureType.TRANSIENT_PROVIDER_FAILURE, amount=50.0, provider="MOCK_PROVIDER_A"
    )
    case, _ = service.detect(
        external_reference=ctx["external_reference"],
        source_type=RevenueSourceType.ONE_OFF_PAYMENT,
        amount=50.0, currency="INR",
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        failure_reason=ctx["failure_reason"], provider=ctx["provider"],
    )
    provider_health = payment_client.get_provider_health_snapshot()
    analysis = await service.analyze(case, provider_health=provider_health)

    assert analysis.diagnosis == "BELOW_ECONOMIC_FLOOR"
    step = case.plans[-1].steps[0]
    assert step.action.value == "NO_ACTION"


async def test_reconciliation_open_blocks_execution_even_with_a_plan(db_session):
    payment_client = MockPaymentSystemClient(seed=4)
    ai_client = GeminiRecoveryClient()
    service = RecoveryService(db=db_session, payment_client=payment_client, ai_client=ai_client)

    ctx = await payment_client.simulate_payment_failure(
        FailureType.TRANSIENT_PROVIDER_FAILURE, amount=2000.0, provider="MOCK_PROVIDER_A"
    )
    case, _ = service.detect(
        external_reference=ctx["external_reference"],
        source_type=RevenueSourceType.ONE_OFF_PAYMENT,
        amount=2000.0, currency="INR",
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        failure_reason=ctx["failure_reason"], provider=ctx["provider"],
    )
    provider_health = payment_client.get_provider_health_snapshot()
    # Analyze while reconciliation is NOT open, so a real RETRY plan gets proposed...
    await service.analyze(case, provider_health=provider_health)
    assert case.plans[-1].steps[0].action.value in ("RETRY_NOW", "REROUTE")

    # ...but by the time we execute, reconciliation has opened (state changed
    # between decision and execution). Must be blocked at the VALIDATE step,
    # not silently executed.
    result_case = await service.validate_and_execute_next(
        case, provider_health=provider_health, reconciliation_open=True
    )
    assert result_case.status == CaseStatus.STOPPED
    step = result_case.plans[-1].steps[0]
    assert step.status.value == "SKIPPED"
    assert step.outcome == "BLOCKED_AT_EXECUTION_TIME"

