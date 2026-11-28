-- StayScale Part 2: Confirmed Bookings & HTTP Idempotency Registry
-- Note: outbox_events is added in Part 4 when introducing async fulfillment / Kafka.

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id VARCHAR(120) NOT NULL,
    hotel_id UUID NOT NULL REFERENCES hotels(id),
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    hold_id VARCHAR(80) NOT NULL UNIQUE,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    rooms_count INTEGER NOT NULL CHECK (rooms_count > 0),
    guests_count INTEGER NOT NULL CHECK (guests_count > 0),
    nightly_base_minor BIGINT NOT NULL CHECK (nightly_base_minor > 0),
    total_price_minor BIGINT NOT NULL CHECK (total_price_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    pricing_breakdown JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    guest_name VARCHAR(160) NOT NULL,
    guest_email VARCHAR(180) NOT NULL,
    payment_reference VARCHAR(120) NOT NULL,
    experiment_variant VARCHAR(64) NOT NULL DEFAULT 'NONE',
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (check_out > check_in)
);

CREATE INDEX idx_bookings_user_created
    ON bookings (user_id, created_at DESC);

CREATE INDEX idx_bookings_hotel_checkin
    ON bookings (hotel_id, check_in);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(160) PRIMARY KEY,
    endpoint VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    http_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_idempotency_expires_at
    ON idempotency_keys (expires_at);
