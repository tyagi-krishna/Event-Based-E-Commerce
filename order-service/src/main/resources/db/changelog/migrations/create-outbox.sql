-- liquibase formatted sql

-- changeset krishna:2

CREATE TABLE outbox (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     aggregate_type VARCHAR(100) NOT NULL,
     aggregate_id BIGINT NOT NULL,
     event_type VARCHAR(100) NOT NULL,
     payload JSON NOT NULL,
     status VARCHAR(50) NOT NULL,
     retry_count INT DEFAULT 0,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     published_at TIMESTAMP NULL
);
