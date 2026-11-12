
-- ============================================
-- SPLASH & DASH DATABASE
-- PostgreSQL
-- ============================================

-- 1. USERS
-- Stores shared account and login information.

CREATE TABLE users (
    user_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    surname VARCHAR(50) NOT NULL,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    password_hash TEXT,
    firebase_uid VARCHAR(150) UNIQUE,
    account_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

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

-- Make email addresses unique regardless of letter case.

CREATE UNIQUE INDEX uq_users_email
ON users (LOWER(email));


-- 2. ADMINS
-- Stores administrator profiles.

CREATE TABLE admins (
    admin_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,

    CONSTRAINT fk_admin_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);


-- 3. STAFF
-- Stores employee profiles.

CREATE TABLE staff (
    staff_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    gender VARCHAR(10) NOT NULL,

    CONSTRAINT fk_staff_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT chk_staff_gender
        CHECK (gender IN ('Male', 'Female', 'Other'))
);


-- 4. CUSTOMERS
-- Stores customer-specific information.

CREATE TABLE customers (
    customer_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    home_address VARCHAR(255) NOT NULL,

    CONSTRAINT fk_customer_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);


-- 5. SERVICES
-- Stores the available car wash services.

CREATE TABLE services (
    service_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    price NUMERIC(10,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_service_price
        CHECK (price >= 0)
);


-- 6. BOOKINGS
-- Stores customer bookings.

CREATE TABLE bookings (
    booking_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    staff_id INTEGER,
    service_id INTEGER NOT NULL,
    vehicle_type VARCHAR(30) NOT NULL,
    booking_datetime TIMESTAMPTZ NOT NULL,
    location_type VARCHAR(20) NOT NULL,
    booking_address VARCHAR(255),
    booking_status VARCHAR(30) NOT NULL DEFAULT 'Pending',
    total_amount NUMERIC(10,2) NOT NULL,

    CONSTRAINT fk_booking_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT fk_booking_staff
        FOREIGN KEY (staff_id)
        REFERENCES staff(staff_id),

    CONSTRAINT fk_booking_service
        FOREIGN KEY (service_id)
        REFERENCES services(service_id),

    CONSTRAINT chk_vehicle_type
        CHECK (
            vehicle_type IN (
                'Small Car',
                'Large Car',
                'SUV',
                'Bakkie'
            )
        ),

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
        CHECK (
            booking_status IN (
                'Pending',
                'Confirmed',
                'In Progress',
                'Completed',
                'Cancelled'
            )
        ),

    CONSTRAINT chk_booking_amount
        CHECK (total_amount > 0)
);


-- 7. PAYMENTS
-- One full payment record per booking.

CREATE TABLE payments (
    payment_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id INTEGER NOT NULL UNIQUE,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'Pending',
    reference_number VARCHAR(100),
    transaction_id VARCHAR(150),
    paid_at TIMESTAMPTZ,

    CONSTRAINT fk_payment_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings(booking_id),

    CONSTRAINT chk_payment_method
        CHECK (
            payment_method IN ('Cash', 'EFT', 'PayFast')
        ),

    CONSTRAINT chk_payment_status
        CHECK (
            payment_status IN (
                'Pending',
                'Paid',
                'Failed',
                'Refunded'
            )
        ),

    CONSTRAINT chk_payment_amount
        CHECK (amount > 0)
);


-- ============================================
-- INITIAL SERVICE RECORDS
-- Prices must be updated before activation.
-- ============================================

INSERT INTO services
    (service_name, description, price, is_active)
VALUES
    ('External', 'Exterior vehicle wash', 0.00, FALSE),
    ('Internal', 'Interior vehicle cleaning', 0.00, FALSE),
    ('Full', 'Interior and exterior vehicle cleaning', 0.00, FALSE);


-- ============================================
-- BASIC INDEXES
-- ============================================

CREATE INDEX idx_bookings_customer
ON bookings(customer_id);

CREATE INDEX idx_bookings_staff
ON bookings(staff_id);

CREATE INDEX idx_bookings_datetime
ON bookings(booking_datetime);

CREATE INDEX idx_bookings_status
ON bookings(booking_status);

CREATE INDEX idx_payments_status
ON payments(payment_status);
