package com.e_commerce.inventory_service.service;

import com.e_commerce.inventory_service.entity.InventoryItem;
import com.e_commerce.inventory_service.entity.ProcessedEvent;
import com.e_commerce.inventory_service.repository.InventoryItemRepository;
import com.e_commerce.inventory_service.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryItemRepository inventoryItemRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-created", groupId = "inventory-service")
    @Transactional
    public void onOrderEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String eventId = root.path("eventId").asText(null);
            String eventType = root.path("eventType").asText(null);

            if (eventId == null || eventType == null) {
                log.warn("Inventory event missing eventId or eventType, skipping: {}", payload);
                return;
            }

            if (processedEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate inventory event detected eventId={} type={}, skipping", eventId, eventType);
                return;
            }

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

            // Saved in the same transaction as the stock changes — atomic guarantee
            processedEventRepository.save(new ProcessedEvent(eventId, eventType));

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
