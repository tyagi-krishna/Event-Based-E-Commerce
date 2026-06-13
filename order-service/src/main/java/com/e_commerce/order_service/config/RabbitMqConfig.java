package com.e_commerce.order_service.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENT_EXCHANGE = "event-exchange";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }
}
