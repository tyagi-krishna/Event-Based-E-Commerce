package com.e_commerce.product_service.exception;

public class SkuAlreadyExistsException extends RuntimeException {
    public SkuAlreadyExistsException(String sku) {
        super("Product with sku already exists: " + sku);
    }
}
