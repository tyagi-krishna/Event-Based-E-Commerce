package com.e_commerce.notification_service.service;

import com.e_commerce.notification_service.config.RabbitMqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void handleEvent(String payload, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String eventType) {
        String subject = switch (eventType) {
            case RabbitMqConfig.USER_CREATED -> "New user created";
            case RabbitMqConfig.ORDER_CREATED -> "New order received";
            case RabbitMqConfig.ORDER_CONFIRMED -> "Order confirmed";
            case RabbitMqConfig.ORDER_CANCELLED -> "Order cancelled";
            default -> "Event received";
        };

        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            log.info("Notification: {} payload={}", subject, payloadNode.toPrettyString());
        } catch (IOException e) {
            log.warn("Notification payload is not valid JSON for event {}: {}", eventType, payload);
        }
    }
}
