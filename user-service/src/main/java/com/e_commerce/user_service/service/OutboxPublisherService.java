package com.e_commerce.user_service.service;

import com.e_commerce.user_service.config.KafkaTopicConfig;
import com.e_commerce.user_service.entity.OutboxEvent;
import com.e_commerce.user_service.entity.OutboxStatus;
import com.e_commerce.user_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(KafkaTopicConfig.USER_CREATED, event.getPayload());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event id={} eventId={} type={}", event.getId(), event.getEventId(), event.getEventType());
            } catch (Exception exception) {
                log.error("Failed to publish outbox event id={} eventId={}: {}", event.getId(), event.getEventId(), exception.getMessage(), exception);
                event.setRetryCount(event.getRetryCount() == null ? 1 : event.getRetryCount() + 1);
                outboxEventRepository.save(event);
            }
        }
    }
}
