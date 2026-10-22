CREATE TABLE payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    product_code TEXT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL CHECK (currency = lower(currency) AND currency ~ '^[a-z]{3}$'),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED')),
    provider TEXT NOT NULL,
    provider_session_id TEXT UNIQUE,
    provider_payment_intent_id TEXT,
    checkout_url TEXT NOT NULL,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payments_user_created_idx ON payments(user_id, created_at DESC);

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    provider TEXT NOT NULL,
    provider_event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    outcome TEXT NOT NULL CHECK (outcome IN ('PROCESSED', 'IGNORED', 'REJECTED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT payment_webhook_event_unique UNIQUE(provider, provider_event_id)
);
