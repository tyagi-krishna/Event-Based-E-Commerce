CREATE TABLE IF NOT EXISTS notification_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    received_at DATETIME NOT NULL
);
