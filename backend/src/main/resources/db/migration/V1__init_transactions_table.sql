CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount          NUMERIC(19, 4)   NOT NULL,
    currency        VARCHAR(3)       NOT NULL,
    transaction_date TIMESTAMPTZ     NOT NULL,
    description     TEXT             NOT NULL,
    status          VARCHAR(32)      NOT NULL,
    category        VARCHAR(32),
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_category ON transactions (category);
