import json
import subprocess
import uuid
from concurrent.futures import ThreadPoolExecutor

import requests

BASE_URL = 'http://localhost:8081/api/v1'


def setup_merchant(name_prefix):
    payload = {
        'merchantName': f'{name_prefix} {uuid.uuid4().hex[:8]}',
        'email': f'{name_prefix.lower().replace(" ", "_")}_{uuid.uuid4().hex[:8]}@example.com'
    }
    r = requests.post(f'{BASE_URL}/merchants', json=payload, timeout=10)
    if r.status_code == 201:
        data = r.json()
        return {
            'id': data['merchantId'],
            'key': data['apiCredential']['keyId'],
            'secret': data['apiCredential']['plaintextSecret']
        }
    return None


merchant_a_info = setup_merchant('Merchant A')
merchant_b_info = setup_merchant('Merchant B')

MERCHANT_A = merchant_a_info['id'] if merchant_a_info else '1da3393d-a026-4707-ab82-25d0d3818085'
MERCHANT_B = merchant_b_info['id'] if merchant_b_info else 'a680f85c-8de9-420d-b434-af7f8f40c66f'


def post(merchant_info, idem_key, payload):
    headers = {'Content-Type': 'application/json'}
    if isinstance(merchant_info, dict):
        headers['X-Merchant-Id'] = str(merchant_info['id'])
        headers['X-API-Key'] = str(merchant_info['key'])
        headers['X-API-Secret'] = str(merchant_info['secret'])
    elif merchant_info is not None:
        headers['X-Merchant-Id'] = str(merchant_info)
    if idem_key is not None:
        headers['X-Idempotency-Key'] = str(idem_key)
    try:
        r = requests.post(f'{BASE_URL}/payments', headers=headers, json=payload, timeout=20)
        return {
            'status': r.status_code,
            'body': r.json() if r.content else None,
            'ok': r.ok,
        }
    except Exception as exc:  # pragma: no cover - verification helper
        return {'status': 0, 'body': str(exc), 'ok': False}


def sql(query):
    result = subprocess.check_output(
        ['docker', 'exec', 'payment-postgres', 'psql', '-U', 'payment_user', '-d', 'payment_db', '-t', '-c', query],
        text=True,
    )
    return result.strip()


payload_new = {
    'amount': '100.00',
    'currency': 'USD',
    'customerRef': 'cust_new_001',
    'orderRef': 'order_new_001',
    'paymentType': 'PAYMENT',
    'description': 'new key test',
}

payload_same = {
    'amount': '100.00',
    'currency': 'USD',
    'customerRef': 'cust_new_001',
    'orderRef': 'order_new_001',
    'paymentType': 'PAYMENT',
    'description': 'new key test',
}

payload_different = {
    'amount': '200.00',
    'currency': 'USD',
    'customerRef': 'cust_new_001',
    'orderRef': 'order_new_001',
    'paymentType': 'PAYMENT',
    'description': 'different payload',
}

payload_same_req_different_key = {
    'amount': '100.00',
    'currency': 'USD',
    'customerRef': 'cust_same_req_002',
    'orderRef': 'order_same_req_002',
    'paymentType': 'PAYMENT',
    'description': 'same payload different key',
}

payload_concurrent = {
    'amount': '150.00',
    'currency': 'USD',
    'customerRef': 'cust_conc_010',
    'orderRef': 'order_conc_010',
    'paymentType': 'PAYMENT',
    'description': 'concurrent identical',
}

results = {}

results['test_01_new_idempotency_key'] = post(merchant_a_info, 'idem-live-001', payload_new)
results['test_02_same_request_retry_same_key'] = post(merchant_a_info, 'idem-live-001', payload_same)
results['test_03_same_key_different_request'] = post(merchant_a_info, 'idem-live-001', payload_different)
results['test_04_different_keys_same_request_a'] = post(merchant_a_info, 'idem-live-002', payload_same_req_different_key)
results['test_04_different_keys_same_request_b'] = post(merchant_a_info, 'idem-live-003', payload_same_req_different_key)
results['test_05_same_key_different_merchant'] = post(merchant_b_info, 'idem-live-001', payload_new)

with ThreadPoolExecutor(max_workers=10) as executor:
    results['test_06_10_concurrent_identical_requests'] = list(executor.map(lambda unused: post(merchant_a_info, 'idem-live-010', payload_concurrent), range(10)))

keys = [f'idem-live-20{i:02d}' for i in range(1, 11)]
with ThreadPoolExecutor(max_workers=10) as executor:
    results['test_07_10_concurrent_different_keys'] = [
        executor.submit(post, merchant_a_info, key, payload_concurrent).result() for key in keys
    ]

sql(f"UPDATE merchant SET status = 'SUSPENDED' WHERE merchant_id = '{MERCHANT_A}';")
results['test_08_inactive_merchant'] = post(merchant_a_info, 'idem-live-inactive', payload_new)
sql(f"UPDATE merchant SET status = 'ACTIVE' WHERE merchant_id = '{MERCHANT_A}';")

results['test_09_missing_idempotency_key'] = post(merchant_a_info, None, payload_new)
results['test_10_invalid_idempotency_key'] = post(merchant_a_info, '', payload_new)

redis_keys_output = subprocess.check_output(['docker', 'exec', 'payment-redis', 'redis-cli', '-p', '6379', 'KEYS', 'payment:idempotency:*'], text=True)
results['redis_keys'] = redis_keys_output.strip().splitlines() if redis_keys_output.strip() else []

for key in ['idem-live-001', 'idem-live-010', 'idem-live-2001']:
    query = f"SELECT COUNT(*) FROM payment WHERE merchant_id = '{MERCHANT_A}' AND idempotency_key = '{key}';"
    results[f'db_{key}_count'] = sql(query)

# use first successful payment id from the created records for GET regression
payment_id = None
for key in ['test_01_new_idempotency_key', 'test_04_different_keys_same_request_a', 'test_06_10_concurrent_identical_requests']:
    if key == 'test_06_10_concurrent_identical_requests':
        candidates = [item.get('body', {}) for item in results[key] if isinstance(item.get('body'), dict) and item.get('body', {}).get('paymentId')]
        if candidates:
            payment_id = candidates[0].get('paymentId')
            break
    else:
        body = results[key].get('body')
        if isinstance(body, dict) and body.get('paymentId'):
            payment_id = body.get('paymentId')
            break

if payment_id and merchant_a_info:
    try:
        headers = {
            'X-Merchant-Id': str(merchant_a_info['id']),
            'X-API-Key': str(merchant_a_info['key']),
            'X-API-Secret': str(merchant_a_info['secret'])
        }
        r = requests.get(f'{BASE_URL}/payments/{payment_id}', headers=headers, timeout=20)
        results['test_13_existing_api_regression_get_payment'] = {'status': r.status_code, 'body': r.json() if r.content else None}
    except Exception as exc:  # pragma: no cover - verification helper
        results['test_13_existing_api_regression_get_payment'] = {'status': 0, 'body': str(exc)}
else:
    results['test_13_existing_api_regression_get_payment'] = {'status': 0, 'body': 'No payment ID captured'}

print(json.dumps(results, indent=2, default=str))
