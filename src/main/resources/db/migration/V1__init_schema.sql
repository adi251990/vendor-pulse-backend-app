-- HireMe platform initial schema
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- Design note: JPA entities only ever read/write plain double lat/lon columns
-- (simple, driver-agnostic, no spatial Hibernate dialect required). Each
-- geo-bearing table below has a trigger that derives a PostGIS GEOGRAPHY
-- column from those doubles purely so spatial repositories can run
-- ST_DWithin / ST_Contains / GIST-indexed native queries when needed.

-- ===================== IDENTITY =====================
CREATE TYPE user_role AS ENUM ('STAFF', 'OWNER', 'ADMIN');

CREATE TABLE vendors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name          VARCHAR(255) NOT NULL,
    stripe_customer_id  VARCHAR(64) UNIQUE,
    markup_pct_default  NUMERIC(5,4) NOT NULL DEFAULT 0.2200,
    billing_address     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(20)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            user_role    NOT NULL,
    org_id          UUID REFERENCES vendors(id),
    is_active       BOOLEAN NOT NULL DEFAULT true,
    token_version   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_owner_has_org CHECK (role <> 'OWNER' OR org_id IS NOT NULL)
);
CREATE INDEX idx_users_org_id ON users(org_id);

CREATE TABLE staff_profiles (
    user_id                  UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    stripe_account_id        VARCHAR(64) UNIQUE,
    home_lat                 DOUBLE PRECISION,
    home_lon                 DOUBLE PRECISION,
    home_location            GEOGRAPHY(POINT, 4326),
    skill_tags               TEXT[] NOT NULL DEFAULT '{}',
    avg_rating               NUMERIC(3,2) NOT NULL DEFAULT 0,
    reliability_score        NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    background_check_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    face_enrollment_ref      VARCHAR(128),
    no_show_count_90d        INT NOT NULL DEFAULT 0,
    suspended                BOOLEAN NOT NULL DEFAULT false,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_staff_home_location ON staff_profiles USING GIST (home_location);
CREATE INDEX idx_staff_skill_tags ON staff_profiles USING GIN (skill_tags);

-- Per-table trigger functions (Postgres trigger functions can't take column
-- names as arguments cleanly across tables, so each table gets its own thin
-- wrapper calling the shared point-building logic).
CREATE OR REPLACE FUNCTION sync_geography_point_staff() RETURNS trigger AS $$
BEGIN
    IF NEW.home_lon IS NOT NULL AND NEW.home_lat IS NOT NULL THEN
        NEW.home_location := ST_SetSRID(ST_MakePoint(NEW.home_lon, NEW.home_lat), 4326)::geography;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_staff_geo BEFORE INSERT OR UPDATE ON staff_profiles
    FOR EACH ROW EXECUTE FUNCTION sync_geography_point_staff();

-- ===================== SHIFTS & BOOKINGS =====================
CREATE TYPE shift_status AS ENUM (
    'DRAFT','PUBLISHED','MATCHING','FILLED','IN_PROGRESS',
    'COMPLETED','DISPUTED','UNDER_REVIEW','INVOICED','PAID',
    'NO_SHOW','UNFILLED','CANCELLED'
);

CREATE TABLE shifts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id             UUID NOT NULL REFERENCES vendors(id),
    title              VARCHAR(255) NOT NULL,
    required_skills    TEXT[] NOT NULL DEFAULT '{}',
    hourly_rate        NUMERIC(8,2) NOT NULL CHECK (hourly_rate > 0),
    headcount          INT NOT NULL CHECK (headcount > 0),
    filled_count       INT NOT NULL DEFAULT 0,
    site_lat           DOUBLE PRECISION NOT NULL,
    site_lon           DOUBLE PRECISION NOT NULL,
    site_center        GEOGRAPHY(POINT, 4326),
    site_polygon       GEOGRAPHY(POLYGON, 4326),
    geofence_radius_m  INT NOT NULL DEFAULT 150,
    start_time         TIMESTAMPTZ NOT NULL,
    end_time           TIMESTAMPTZ NOT NULL,
    status             shift_status NOT NULL DEFAULT 'DRAFT',
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_time_order CHECK (end_time > start_time),
    CONSTRAINT chk_headcount CHECK (filled_count <= headcount)
);
CREATE INDEX idx_shifts_status_start ON shifts(status, start_time);
CREATE INDEX idx_shifts_site_center ON shifts USING GIST (site_center);
CREATE INDEX idx_shifts_org_id ON shifts(org_id);

CREATE OR REPLACE FUNCTION sync_geography_point_shift() RETURNS trigger AS $$
BEGIN
    NEW.site_center := ST_SetSRID(ST_MakePoint(NEW.site_lon, NEW.site_lat), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_shift_geo BEFORE INSERT OR UPDATE ON shifts
    FOR EACH ROW EXECUTE FUNCTION sync_geography_point_shift();

CREATE TYPE booking_status AS ENUM ('CLAIMED','ACTIVE','COMPLETED','NO_SHOW','CANCELLED');

CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id            UUID NOT NULL REFERENCES shifts(id),
    staff_id            UUID NOT NULL REFERENCES users(id),
    status              booking_status NOT NULL DEFAULT 'CLAIMED',
    match_score         NUMERIC(6,4),
    claimed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_backup_dispatch  BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (shift_id, staff_id)
);
CREATE INDEX idx_bookings_staff_id ON bookings(staff_id, status);
CREATE INDEX idx_bookings_shift_id ON bookings(shift_id);

-- ===================== TIMESHEETS & GEOLOGS =====================
CREATE TYPE geo_event_type AS ENUM ('CLOCK_IN','CLOCK_OUT','BREAK_START','BREAK_END');

CREATE TABLE geo_logs (
    id                          BIGSERIAL PRIMARY KEY,
    booking_id                  UUID NOT NULL REFERENCES bookings(id),
    event_type                  geo_event_type NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    lat                         DOUBLE PRECISION NOT NULL,
    lon                         DOUBLE PRECISION NOT NULL,
    location                    GEOGRAPHY(POINT, 4326),
    accuracy_m                  NUMERIC(6,2),
    within_geofence             BOOLEAN NOT NULL,
    device_attestation_ok       BOOLEAN NOT NULL,
    mock_location_suspected     BOOLEAN NOT NULL DEFAULT false,
    selfie_verification_score   NUMERIC(4,3),
    signature                   VARCHAR(255) NOT NULL
);
CREATE INDEX idx_geologs_booking_id ON geo_logs(booking_id, event_type);
CREATE INDEX idx_geologs_location ON geo_logs USING GIST (location);

CREATE OR REPLACE FUNCTION sync_geography_point_geolog() RETURNS trigger AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.lon, NEW.lat), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_geolog_geo BEFORE INSERT OR UPDATE ON geo_logs
    FOR EACH ROW EXECUTE FUNCTION sync_geography_point_geolog();

CREATE TYPE timesheet_status AS ENUM (
    'PENDING_APPROVAL','APPROVED','DISPUTED','UNDER_REVIEW','ADJUSTED','FINALIZED'
);

CREATE TABLE timesheets (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id             UUID NOT NULL UNIQUE REFERENCES bookings(id),
    clock_in_log_id        BIGINT REFERENCES geo_logs(id),
    clock_out_log_id       BIGINT REFERENCES geo_logs(id),
    regular_hours          NUMERIC(5,2) NOT NULL DEFAULT 0,
    overtime_hours         NUMERIC(5,2) NOT NULL DEFAULT 0,
    holiday_hours          NUMERIC(5,2) NOT NULL DEFAULT 0,
    base_worker_pay        NUMERIC(10,2),
    platform_markup_fee    NUMERIC(10,2),
    vendor_bill_rate       NUMERIC(10,2),
    status                 timesheet_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    dispute_reason         TEXT,
    approved_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_timesheets_status ON timesheets(status);

-- ===================== PAYMENTS =====================
CREATE TYPE invoice_status AS ENUM ('PENDING','PAID','FAILED','ESCROWED','REFUNDED');

CREATE TABLE invoices (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timesheet_id                UUID NOT NULL UNIQUE REFERENCES timesheets(id),
    stripe_payment_intent_id    VARCHAR(64) UNIQUE,
    amount_charged              NUMERIC(10,2) NOT NULL,
    platform_fee                NUMERIC(10,2) NOT NULL,
    worker_net_pay               NUMERIC(10,2) NOT NULL,
    status                      invoice_status NOT NULL DEFAULT 'PENDING',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at                     TIMESTAMPTZ
);

CREATE TABLE disputes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timesheet_id        UUID NOT NULL REFERENCES timesheets(id),
    raised_by           UUID NOT NULL REFERENCES users(id),
    reason              TEXT NOT NULL,
    escrow_invoice_id   UUID REFERENCES invoices(id),
    assigned_admin_id   UUID REFERENCES users(id),
    resolution          TEXT,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ===================== AUDIT =====================
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   VARCHAR(100) NOT NULL,
    before_data JSONB,
    after_data  JSONB,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
