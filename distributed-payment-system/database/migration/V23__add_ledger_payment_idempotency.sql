CREATE UNIQUE INDEX ux_ledger_transaction_payment
    ON ledger_transaction(payment_id)
    WHERE transaction_type = 'PAYMENT' AND payment_id IS NOT NULL;
