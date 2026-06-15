package com.e_commerce.order_service.controller;

import com.e_commerce.order_service.config.KafkaTopicConfig;
import com.e_commerce.order_service.entity.OutboxEvent;
import com.e_commerce.order_service.entity.OutboxStatus;
import com.e_commerce.order_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/simulate")
@Slf4j
public class SimulationController {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/duplicate-event")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void republishLastEvent() {
        Optional<OutboxEvent> lastPublished = outboxEventRepository.findTopByStatusOrderByPublishedAtDesc(OutboxStatus.PUBLISHED);
        if (lastPublished.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No published events found to replay");
        }
        OutboxEvent event = lastPublished.get();
        kafkaTemplate.send(KafkaTopicConfig.ORDER_CREATED, event.getPayload());
        log.info("[SIMULATE] Replayed eventId={} type={} — idempotent consumers should reject it", event.getEventId(), event.getEventType());
    }
}
