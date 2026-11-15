-- ============================================================
-- SPLASH & DASH DATABASE (PostgreSQL)
--
-- This script is IDEMPOTENT: it is executed on every API start-up
-- (see Program.cs), so every statement must be safe to run again.
--   * CREATE ... IF NOT EXISTS   creates objects on a fresh database
--   * ALTER ... ADD COLUMN IF NOT EXISTS  upgrades older databases
-- It never drops tables or deletes data.
-- ============================================================


-- ------------------------------------------------------------
-- 1. USERS
-- Shared account and login information for Admin, Staff, Customer.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id        INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     VARCHAR(50)  NOT NULL,
    surname        VARCHAR(50)  NOT NULL,
    username       VARCHAR(50)  UNIQUE,
    email          VARCHAR(150) NOT NULL,
    phone_number   VARCHAR(20)  NOT NULL,
    gender         VARCHAR(10)  NOT NULL DEFAULT 'Undefined',
    password_hash  TEXT,
    firebase_uid   VARCHAR(150) UNIQUE,
    account_type   VARCHAR(20)  NOT NULL,
    is_super_admin BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_type
        CHECK (account_type IN ('Admin', 'Staff', 'Customer')),

    CONSTRAINT chk_account_credentials
        CHECK (
            (account_type = 'Customer'
                AND (password_hash IS NOT NULL OR firebase_uid IS NOT NULL))
            OR
            (account_type IN ('Admin', 'Staff')
                AND password_hash IS NOT NULL)
        ),

    CONSTRAINT chk_username_required
        CHECK (
            (account_type = 'Customer')
            OR
            (account_type IN ('Admin', 'Staff')
                AND username IS NOT NULL)
        )
);

-- Columns added after the first release (no-ops on up-to-date databases).
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR(10) NOT NULL DEFAULT 'Undefined';
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Email addresses are unique regardless of letter case.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email
    ON users (LOWER(email));


-- ------------------------------------------------------------
-- 2. ADMINS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admins (
    admin_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id  INTEGER NOT NULL UNIQUE,

    CONSTRAINT fk_admin_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
);


-- ------------------------------------------------------------
-- 3. STAFF
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staff (
    staff_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id  INTEGER NOT NULL UNIQUE,
    gender   VARCHAR(10) NOT NULL,

    CONSTRAINT fk_staff_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- The API stores 'Undefined' when no gender is chosen, so the original
-- check (Male/Female/Other only) would reject those staff accounts.
-- The new list is a superset of the old one, so existing rows stay valid.
ALTER TABLE staff DROP CONSTRAINT IF EXISTS chk_staff_gender;
ALTER TABLE staff
    ADD CONSTRAINT chk_staff_gender
    CHECK (gender IN ('Male', 'Female', 'Other', 'Undefined'));


-- ------------------------------------------------------------
-- 4. CUSTOMERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    customer_id  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      INTEGER NOT NULL UNIQUE,
    home_address VARCHAR(255) NOT NULL,

    CONSTRAINT fk_customer_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
);


-- ------------------------------------------------------------
-- 5. SERVICES
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS services (
    service_id   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL UNIQUE,
    description  TEXT,
    price        NUMERIC(10,2) NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_service_price CHECK (price >= 0)
);


-- ------------------------------------------------------------
-- 6. BOOKINGS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    booking_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id      INTEGER NOT NULL,
    staff_id         INTEGER,
    service_id       INTEGER NOT NULL,
    vehicle_type     VARCHAR(30) NOT NULL,
    booking_datetime TIMESTAMPTZ NOT NULL,
    location_type    VARCHAR(20) NOT NULL,
    booking_address  VARCHAR(255),
    booking_status   VARCHAR(30) NOT NULL DEFAULT 'Pending',
    total_amount     NUMERIC(10,2) NOT NULL,

    CONSTRAINT fk_booking_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id),

    CONSTRAINT fk_booking_staff
        FOREIGN KEY (staff_id) REFERENCES staff(staff_id),

    CONSTRAINT fk_booking_service
        FOREIGN KEY (service_id) REFERENCES services(service_id),

    CONSTRAINT chk_vehicle_type
        CHECK (vehicle_type IN ('Small Car', 'Large Car', 'SUV', 'Bakkie')),

    CONSTRAINT chk_location_type
        CHECK (location_type IN ('On-site', 'House Call')),

    CONSTRAINT chk_booking_address
        CHECK (
            (location_type = 'House Call'
                AND booking_address IS NOT NULL
                AND LENGTH(TRIM(booking_address)) > 0)
            OR
            (location_type = 'On-site')
        ),

    CONSTRAINT chk_booking_status
        CHECK (booking_status IN
            ('Pending', 'Confirmed', 'In Progress', 'Completed', 'Cancelled')),

    CONSTRAINT chk_booking_amount CHECK (total_amount > 0)
);


-- ------------------------------------------------------------
-- 7. PAYMENTS
-- One payment record per booking.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    payment_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id       INTEGER NOT NULL UNIQUE,
    payment_method   VARCHAR(20) NOT NULL,
    amount           NUMERIC(10,2) NOT NULL,
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'Pending',
    reference_number VARCHAR(100),
    transaction_id   VARCHAR(150),
    paid_at          TIMESTAMPTZ,

    CONSTRAINT fk_payment_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),

    CONSTRAINT chk_payment_method
        CHECK (payment_method IN ('Cash', 'EFT', 'PayFast')),

    CONSTRAINT chk_payment_status
        CHECK (payment_status IN ('Pending', 'Paid', 'Failed', 'Refunded')),

    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);


-- ------------------------------------------------------------
-- INITIAL SERVICE RECORDS
-- Only inserts services that do not exist yet, so prices you change
-- later are never overwritten on restart. Update prices as required.
-- ------------------------------------------------------------
INSERT INTO services (service_name, description, price, is_active)
VALUES
    ('External Wash',     'Exterior vehicle wash',                  150.00, TRUE),
    ('Interior Cleaning', 'Interior vehicle cleaning',              200.00, TRUE),
    ('Full Detail',       'Interior and exterior vehicle cleaning', 350.00, TRUE)
ON CONFLICT (service_name) DO NOTHING;


-- ------------------------------------------------------------
-- INDEXES
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_bookings_customer ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_staff    ON bookings(staff_id);
CREATE INDEX IF NOT EXISTS idx_bookings_datetime ON bookings(booking_datetime);
CREATE INDEX IF NOT EXISTS idx_bookings_status   ON bookings(booking_status);
CREATE INDEX IF NOT EXISTS idx_payments_status   ON payments(payment_status);