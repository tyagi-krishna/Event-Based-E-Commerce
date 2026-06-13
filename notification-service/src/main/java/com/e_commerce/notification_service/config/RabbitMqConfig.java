package com.e_commerce.notification_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENT_EXCHANGE = "event-exchange";
    public static final String NOTIFICATION_QUEUE = "notification-service.queue";
    public static final String USER_CREATED = "UserCreated";
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCELLED = "OrderCancelled";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding userCreatedBinding(Queue notificationQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventExchange).with(USER_CREATED);
    }

    @Bean
    public Binding orderCreatedBinding(Queue notificationQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventExchange).with(ORDER_CREATED);
    }

    @Bean
    public Binding orderConfirmedBinding(Queue notificationQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventExchange).with(ORDER_CONFIRMED);
    }

    @Bean
    public Binding orderCancelledBinding(Queue notificationQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventExchange).with(ORDER_CANCELLED);
    }
}
