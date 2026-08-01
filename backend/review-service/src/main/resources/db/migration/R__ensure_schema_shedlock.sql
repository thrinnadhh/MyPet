CREATE TABLE IF NOT EXISTS reviews.shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON reviews.shedlock TO review_service_role;
