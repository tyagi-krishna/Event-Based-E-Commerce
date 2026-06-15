package com.e_commerce.user_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String USER_CREATED = "user-created";

    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name(USER_CREATED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
