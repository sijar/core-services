ALTER TABLE users ADD COLUMN phone_number TEXT;
ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD CONSTRAINT users_phone_e164 CHECK (phone_number IS NULL OR phone_number ~ '^\+[1-9][0-9]{7,14}$');

CREATE TABLE phone_verification_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL CHECK (phone_number ~ '^\+[1-9][0-9]{7,14}$'),
    code_hash CHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX phone_challenge_user_latest_idx ON phone_verification_challenges(user_id, created_at DESC);
CREATE INDEX phone_challenge_expiry_idx ON phone_verification_challenges(expires_at) WHERE consumed_at IS NULL;
