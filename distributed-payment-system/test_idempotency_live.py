import uuid
from concurrent.futures import ThreadPoolExecutor
import pytest
import requests

BASE_URL = "http://localhost:8081/api/v1"


@pytest.fixture(scope="module")
def merchant_a():
    """Create Merchant A with provisioned API credentials."""
    payload = {
        "merchantName": f"Merchant A {uuid.uuid4().hex[:8]}",
        "email": f"merchant_a_{uuid.uuid4().hex[:8]}@example.com"
    }
    r = requests.post(f"{BASE_URL}/merchants", json=payload, timeout=10)
    assert r.status_code == 201, f"Failed to create Merchant A: {r.text}"
    data = r.json()
    return {
        "id": data["merchantId"],
        "key": data["apiCredential"]["keyId"],
        "secret": data["apiCredential"]["plaintextSecret"]
    }


@pytest.fixture(scope="module")
def merchant_b():
    """Create Merchant B with provisioned API credentials."""
    payload = {
        "merchantName": f"Merchant B {uuid.uuid4().hex[:8]}",
        "email": f"merchant_b_{uuid.uuid4().hex[:8]}@example.com"
    }
    r = requests.post(f"{BASE_URL}/merchants", json=payload, timeout=10)
    assert r.status_code == 201, f"Failed to create Merchant B: {r.text}"
    data = r.json()
    return {
        "id": data["merchantId"],
        "key": data["apiCredential"]["keyId"],
        "secret": data["apiCredential"]["plaintextSecret"]
    }


def make_headers(merchant, idem_key=None):
    headers = {
        "Content-Type": "application/json",
        "X-Merchant-Id": merchant["id"],
        "X-API-Key": merchant["key"],
        "X-API-Secret": merchant["secret"]
    }
    if idem_key is not None:
        headers["X-Idempotency-Key"] = str(idem_key)
    return headers


def test_01_create_payment_with_new_idempotency_key(merchant_a):
    idem_key = f"idem-{uuid.uuid4()}"
    payload = {
        "amount": "100.00",
        "currency": "USD",
        "customerRef": "cust_001",
        "orderRef": "order_001",
        "paymentType": "PAYMENT",
        "description": "Test initial payment"
    }
    r = requests.post(
        f"{BASE_URL}/payments",
        headers=make_headers(merchant_a, idem_key),
        json=payload,
        timeout=10
    )
    assert r.status_code == 201
    data = r.json()
    assert data["idempotencyKey"] == idem_key
    assert data["merchantId"] == merchant_a["id"]
    assert "paymentId" in data


def test_02_same_request_retry_same_idempotency_key(merchant_a):
    idem_key = f"idem-{uuid.uuid4()}"
    payload = {
        "amount": "120.00",
        "currency": "USD",
        "customerRef": "cust_002",
        "orderRef": "order_002",
        "paymentType": "PAYMENT",
        "description": "Idempotent payment test"
    }
    headers = make_headers(merchant_a, idem_key)
    r1 = requests.post(f"{BASE_URL}/payments", headers=headers, json=payload, timeout=10)
    assert r1.status_code == 201
    res1 = r1.json()

    # Retry same request with same key
    r2 = requests.post(f"{BASE_URL}/payments", headers=headers, json=payload, timeout=10)
    assert r2.status_code in (200, 201)
    res2 = r2.json()
    assert res1["paymentId"] == res2["paymentId"]


def test_03_different_keys_same_payload_creates_distinct_payments(merchant_a):
    payload = {
        "amount": "50.00",
        "currency": "USD",
        "customerRef": "cust_003",
        "orderRef": "order_003",
        "paymentType": "PAYMENT",
        "description": "Distinct keys test"
    }
    r1 = requests.post(
        f"{BASE_URL}/payments",
        headers=make_headers(merchant_a, f"idem-key-a-{uuid.uuid4()}"),
        json=payload,
        timeout=10
    )
    r2 = requests.post(
        f"{BASE_URL}/payments",
        headers=make_headers(merchant_a, f"idem-key-b-{uuid.uuid4()}"),
        json=payload,
        timeout=10
    )
    assert r1.status_code == 201
    assert r2.status_code == 201
    assert r1.json()["paymentId"] != r2.json()["paymentId"]


def test_04_same_key_different_merchants(merchant_a, merchant_b):
    shared_idem_key = f"shared-key-{uuid.uuid4()}"
    payload = {
        "amount": "75.00",
        "currency": "USD",
        "customerRef": "cust_shared",
        "orderRef": "order_shared",
        "paymentType": "PAYMENT",
        "description": "Merchant isolation test"
    }
    r1 = requests.post(
        f"{BASE_URL}/payments",
        headers=make_headers(merchant_a, shared_idem_key),
        json=payload,
        timeout=10
    )
    r2 = requests.post(
        f"{BASE_URL}/payments",
        headers=make_headers(merchant_b, shared_idem_key),
        json=payload,
        timeout=10
    )
    assert r1.status_code == 201
    assert r2.status_code == 201
    assert r1.json()["merchantId"] != r2.json()["merchantId"]


def test_05_concurrent_identical_requests(merchant_a):
    idem_key = f"concurrent-{uuid.uuid4()}"
    payload = {
        "amount": "150.00",
        "currency": "USD",
        "customerRef": "cust_conc",
        "orderRef": "order_conc",
        "paymentType": "PAYMENT",
        "description": "Concurrent test"
    }
    headers = make_headers(merchant_a, idem_key)

    def send_req(_):
        return requests.post(f"{BASE_URL}/payments", headers=headers, json=payload, timeout=10)

    with ThreadPoolExecutor(max_workers=5) as executor:
        responses = list(executor.map(send_req, range(5)))

    statuses = [r.status_code for r in responses]
    assert all(s in (200, 201, 409) for s in statuses)
    successful = [r.json()["paymentId"] for r in responses if r.status_code in (200, 201)]
    # All successful responses must return the same payment ID
    assert len(set(successful)) == 1


def test_06_missing_api_credentials_rejected():
    payload = {
        "amount": "100.00",
        "currency": "USD",
        "paymentType": "PAYMENT"
    }
    r = requests.post(f"{BASE_URL}/payments", json=payload, timeout=10)
    assert r.status_code == 401


def test_07_get_payment_by_id(merchant_a):
    idem_key = f"idem-get-{uuid.uuid4()}"
    payload = {
        "amount": "30.00",
        "currency": "USD",
        "customerRef": "cust_get",
        "orderRef": "order_get",
        "paymentType": "PAYMENT",
        "description": "Get payment test"
    }
    create_res = requests.post(
        f"{BASE_URL}/payments",
        headers=make_headers(merchant_a, idem_key),
        json=payload,
        timeout=10
    )
    assert create_res.status_code == 201
    payment_id = create_res.json()["paymentId"]

    # Retrieve payment
    get_res = requests.get(
        f"{BASE_URL}/payments/{payment_id}",
        headers=make_headers(merchant_a),
        timeout=10
    )
    assert get_res.status_code == 200
    assert get_res.json()["paymentId"] == payment_id
