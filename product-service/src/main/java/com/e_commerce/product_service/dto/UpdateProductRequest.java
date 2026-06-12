package com.e_commerce.product_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(max = 100) String sku,
        @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @DecimalMin(value = "0.00", inclusive = false) BigDecimal price,
        @Min(0) Integer stockQuantity,
        Boolean active
) {
}
