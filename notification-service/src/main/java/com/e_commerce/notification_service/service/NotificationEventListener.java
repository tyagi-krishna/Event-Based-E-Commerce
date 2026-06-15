package com.e_commerce.notification_service.service;

import com.e_commerce.notification_service.entity.ProcessedEvent;
import com.e_commerce.notification_service.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final SimulationService simulationService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0))
    @KafkaListener(topics = {"user-created", "order-created"}, groupId = "notification-service")
    @Transactional
    public void handleEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String eventId = root.path("eventId").asText(null);
            String eventType = root.path("eventType").asText(null);

            if (eventId == null || eventType == null) {
                log.warn("Notification event missing eventId or eventType, skipping: {}", payload);
                return;
            }

            if (simulationService.shouldFail()) {
                log.warn("[SIMULATE] Notification failure mode active — throwing to trigger retry chain. eventId={}", eventId);
                throw new RuntimeException("Simulated notification failure");
            }

            if (processedEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate notification event detected eventId={} type={}, skipping", eventId, eventType);
                return;
            }

            String subject = switch (eventType) {
                case "UserCreated"    -> "Welcome! New user registered";
                case "OrderCreated"   -> "Your order has been received";
                case "OrderConfirmed" -> "Your order has been confirmed";
                case "OrderCancelled" -> "Your order has been cancelled";
                default               -> "Notification: " + eventType;
            };

            // Simulated email/SMS send
            log.info("[NOTIFICATION] {} | eventId={} | payload={}", subject, eventId, root.toPrettyString());

            // Saved in the same transaction as the log — atomic guarantee
            processedEventRepository.save(new ProcessedEvent(eventId, eventType));

        } catch (IOException e) {
            log.warn("Notification payload is not valid JSON: {}", payload);
        }
    }
}
