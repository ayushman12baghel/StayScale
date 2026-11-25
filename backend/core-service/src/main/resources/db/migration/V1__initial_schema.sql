-- StayScale Part 1: Core Hotels, Room Types & Date-Partitioned Inventory Schema
-- Note: bookings, idempotency_keys, and outbox_events are added in Parts 2 & 4
-- so each phase stays clean and verifiable end-to-end.

CREATE TABLE hotels (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    city VARCHAR(80) NOT NULL,
    country VARCHAR(80) NOT NULL,
    neighborhood VARCHAR(120) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    landmark_name VARCHAR(160) NOT NULL,
    distance_to_center_km NUMERIC(5, 2) NOT NULL,
    star_rating SMALLINT NOT NULL CHECK (star_rating BETWEEN 1 AND 5),
    guest_score NUMERIC(3, 1) NOT NULL CHECK (guest_score BETWEEN 0.0 AND 10.0),
    review_count INTEGER NOT NULL DEFAULT 0 CHECK (review_count >= 0),
    property_type VARCHAR(60) NOT NULL,
    description TEXT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    gallery_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    amenities JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hotels_city_score
    ON hotels (LOWER(city), guest_score DESC);

CREATE INDEX idx_hotels_city_stars
    ON hotels (LOWER(city), star_rating DESC);

CREATE INDEX idx_hotels_amenities_gin
    ON hotels USING GIN (amenities);

CREATE TABLE room_types (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    bed_type VARCHAR(80) NOT NULL,
    room_size_sqm INTEGER NOT NULL CHECK (room_size_sqm > 0),
    max_guests SMALLINT NOT NULL CHECK (max_guests > 0),
    base_price_minor BIGINT NOT NULL CHECK (base_price_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    total_units INTEGER NOT NULL CHECK (total_units > 0),
    free_cancellation BOOLEAN NOT NULL DEFAULT TRUE,
    breakfast_included BOOLEAN NOT NULL DEFAULT FALSE,
    image_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_room_types_hotel_guests
    ON room_types (hotel_id, max_guests, base_price_minor);

CREATE TABLE room_inventory_by_date (
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    stay_date DATE NOT NULL,
    total_rooms INTEGER NOT NULL CHECK (total_rooms > 0),
    available_rooms INTEGER NOT NULL CHECK (available_rooms >= 0 AND available_rooms <= total_rooms),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (room_type_id, stay_date)
);

-- Covering index matching multi-night availability lookups:
-- WHERE stay_date >= :checkIn AND stay_date < :checkOut AND available_rooms >= :rooms
CREATE INDEX idx_inventory_date_avail_room
    ON room_inventory_by_date (stay_date, available_rooms, room_type_id);
