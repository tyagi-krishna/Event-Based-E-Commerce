CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    quantity_on_hand INT NOT NULL,
    updated_at DATETIME NOT NULL
);
