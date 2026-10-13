CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('LEARNER', 'ADMIN'))
);

CREATE TABLE learning_resources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    type VARCHAR(30) NOT NULL,
    location VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_resource_type CHECK (type IN ('STUDY_ROOM', 'MENTORING', 'WORKSHOP'))
);
CREATE INDEX ix_resources_discovery ON learning_resources (active, type, id);

CREATE TABLE availability_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_slots_resource FOREIGN KEY (resource_id) REFERENCES learning_resources(id),
    CONSTRAINT ck_slot_interval CHECK (ends_at > starts_at)
);
CREATE INDEX ix_slots_resource_time ON availability_slots (resource_id, active, starts_at, ends_at);

CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6),
    completed_at DATETIME(6),
    CONSTRAINT fk_bookings_learner FOREIGN KEY (learner_id) REFERENCES users(id),
    CONSTRAINT fk_bookings_resource FOREIGN KEY (resource_id) REFERENCES learning_resources(id),
    CONSTRAINT fk_bookings_slot FOREIGN KEY (slot_id) REFERENCES availability_slots(id),
    CONSTRAINT ck_booking_interval CHECK (ends_at > starts_at),
    CONSTRAINT ck_booking_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED'))
);
CREATE INDEX ix_bookings_conflict ON bookings (resource_id, status, starts_at, ends_at);
CREATE INDEX ix_bookings_history ON bookings (learner_id, created_at, id);
CREATE INDEX ix_bookings_completion ON bookings (status, ends_at);
