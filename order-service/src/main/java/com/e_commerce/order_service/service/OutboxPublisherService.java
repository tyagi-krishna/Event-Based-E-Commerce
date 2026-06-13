package com.e_commerce.order_service.service;

import com.e_commerce.order_service.entity.OutboxEvent;
import com.e_commerce.order_service.entity.OutboxStatus;
import com.e_commerce.order_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "30000")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pendingEvents) {
            try {
                rabbitTemplate.convertAndSend("event-exchange", event.getEventType(), event.getPayload());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event {} type={}", event.getId(), event.getEventType());
            } catch (Exception exception) {
                log.error("Failed to publish outbox event {} type={}: {}", event.getId(), event.getEventType(), exception.getMessage(), exception);
                event.setRetryCount(event.getRetryCount() == null ? 1 : event.getRetryCount() + 1);
                outboxEventRepository.save(event);
            }
        }
    }
}
