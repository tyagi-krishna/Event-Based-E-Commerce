package com.e_commerce.inventory_service.service;

import com.e_commerce.inventory_service.config.RabbitMqConfig;
import com.e_commerce.inventory_service.entity.InventoryItem;
import com.e_commerce.inventory_service.repository.InventoryItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryItemRepository inventoryItemRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.INVENTORY_QUEUE)
    @Transactional
    public void onOrderEvent(String payload,
                             @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String eventType) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                log.warn("Inventory event payload missing items array: {}", payload);
                return;
            }
            for (JsonNode itemNode : items) {
                Long productId = itemNode.path("productId").asLong();
                int quantity = itemNode.path("quantity").asInt();
                if (productId == 0 || quantity <= 0) {
                    log.warn("Skipping invalid inventory item in event {}: {}", eventType, itemNode);
                    continue;
                }
                if ("OrderCancelled".equals(eventType)) {
                    adjustStock(productId, quantity);
                } else {
                    reduceStock(productId, quantity);
                }
            }
        } catch (IOException exception) {
            log.error("Failed to parse inventory event payload: {}", exception.getMessage(), exception);
        }
    }

    private void reduceStock(Long productId, int quantity) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseGet(() -> new InventoryItem(null, productId, 0, LocalDateTime.now()));
        int updatedQuantity = Math.max(0, item.getQuantityOnHand() - quantity);
        item.setQuantityOnHand(updatedQuantity);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);
        log.info("Reduced inventory for product {} by {}. New quantity={}", productId, quantity, updatedQuantity);
    }

    private void adjustStock(Long productId, int quantity) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseGet(() -> new InventoryItem(null, productId, 0, LocalDateTime.now()));
        item.setQuantityOnHand(item.getQuantityOnHand() + quantity);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);
        log.info("Returned inventory for product {} by {}. New quantity={}", productId, quantity, item.getQuantityOnHand());
    }
}
