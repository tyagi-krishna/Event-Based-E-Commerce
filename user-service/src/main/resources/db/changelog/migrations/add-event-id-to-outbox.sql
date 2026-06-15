-- liquibase formatted sql

-- changeset krishna:2
ALTER TABLE outbox ADD COLUMN event_id VARCHAR(36) NOT NULL DEFAULT (UUID());
ALTER TABLE outbox ADD CONSTRAINT uq_outbox_event_id UNIQUE (event_id);
