package com.e_commerce.inventory_service.controller;

import com.e_commerce.inventory_service.dto.InventoryAdjustmentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @PostMapping("/adjust")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjustStock(@Valid @RequestBody InventoryAdjustmentRequest request) {
        // no-op: inventory updates are driven by events in this service
    }
}
