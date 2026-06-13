package com.e_commerce.inventory_service.dto;

public record InventoryAdjustmentRequest(Long productId, Integer quantity) {
}
