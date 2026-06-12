-- liquibase formatted sql

-- changeset krishna:1

CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    sku VARCHAR(100) NOT NULL UNIQUE,

    name VARCHAR(255) NOT NULL,

    description TEXT NULL,

    price DECIMAL(12, 2) NOT NULL,

    stock_quantity INT NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
