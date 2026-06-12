package com.e_commerce.order_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderItemRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = 100) String sku,
        @NotNull @Min(1) Integer quantity,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal unitPrice
) {
}
