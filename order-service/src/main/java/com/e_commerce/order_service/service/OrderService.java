package com.e_commerce.order_service.service;

import com.e_commerce.order_service.dto.CreateOrderItemRequest;
import com.e_commerce.order_service.dto.CreateOrderRequest;
import com.e_commerce.order_service.dto.OrderResponse;
import com.e_commerce.order_service.dto.UpdateOrderStatusRequest;
import com.e_commerce.order_service.entity.CustomerOrder;
import com.e_commerce.order_service.entity.OrderItem;
import com.e_commerce.order_service.entity.OrderStatus;
import com.e_commerce.order_service.entity.OutboxEvent;
import com.e_commerce.order_service.entity.OutboxStatus;
import com.e_commerce.order_service.exception.InvalidOrderStatusException;
import com.e_commerce.order_service.exception.OrderNotFoundException;
import com.e_commerce.order_service.repository.OrderRepository;
import com.e_commerce.order_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        CustomerOrder order = new CustomerOrder();
        order.setUserId(request.userId());
        order.setStatus(OrderStatus.CREATED);

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.items()) {
            OrderItem item = buildOrderItem(itemRequest);
            total = total.add(item.getLineTotal());
            order.addItem(item);
        }
        order.setTotalAmount(total);

        CustomerOrder savedOrder = orderRepository.save(order);
        outboxEventRepository.save(orderEvent(savedOrder, "OrderCreated"));

        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return orderRepository.findAll()
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return OrderResponse.from(findOrder(id));
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        CustomerOrder order = findOrder(id);
        validateStatusChange(order.getStatus(), request.status());
        order.setStatus(request.status());
        order.setUpdatedAt(LocalDateTime.now());

        CustomerOrder savedOrder = orderRepository.save(order);
        outboxEventRepository.save(orderEvent(savedOrder, eventTypeForStatus(request.status())));

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        return updateOrderStatus(id, new UpdateOrderStatusRequest(OrderStatus.CANCELLED));
    }

    private CustomerOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private OrderItem buildOrderItem(CreateOrderItemRequest request) {
        OrderItem item = new OrderItem();
        item.setProductId(request.productId());
        item.setSku(normalizeSku(request.sku()));
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        item.setLineTotal(request.unitPrice().multiply(BigDecimal.valueOf(request.quantity())));
        return item;
    }

    private void validateStatusChange(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.CANCELLED || current == OrderStatus.COMPLETED) {
            throw new InvalidOrderStatusException("Cannot change status for " + current + " order");
        }
        if (current == next) {
            throw new InvalidOrderStatusException("Order is already " + next);
        }
    }

    private String eventTypeForStatus(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "OrderConfirmed";
            case CANCELLED -> "OrderCancelled";
            case COMPLETED -> "OrderCompleted";
            case CREATED -> "OrderStatusChanged";
        };
    }

    private OutboxEvent orderEvent(CustomerOrder order, String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Order");
        event.setAggregateId(order.getId());
        event.setEventType(eventType);
        event.setStatus(OutboxStatus.PENDING);
        event.setPayload(orderPayload(order));
        return event;
    }

    private String orderPayload(CustomerOrder order) {
        return """
                {"id":%d,"userId":%d,"status":"%s","totalAmount":%s,"items":%s,"createdAt":"%s","updatedAt":"%s"}
                """.formatted(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                itemsPayload(order),
                order.getCreatedAt(),
                order.getUpdatedAt()
        ).trim();
    }

    private String itemsPayload(CustomerOrder order) {
        return order.getItems()
                .stream()
                .map(item -> """
                        {"productId":%d,"sku":"%s","quantity":%d,"unitPrice":%s,"lineTotal":%s}
                        """.formatted(
                        item.getProductId(),
                        escapeJson(item.getSku()),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()
                ).trim())
                .toList()
                .toString();
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
