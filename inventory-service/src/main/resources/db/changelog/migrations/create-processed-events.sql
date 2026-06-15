-- liquibase formatted sql

-- changeset krishna:2
CREATE TABLE processed_events (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id     VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_processed_event_id UNIQUE (event_id)
);
