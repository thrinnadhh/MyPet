CREATE TABLE IF NOT EXISTS notifications.shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON notifications.shedlock TO notification_service_role;
