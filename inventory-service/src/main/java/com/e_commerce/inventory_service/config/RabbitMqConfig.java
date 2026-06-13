package com.e_commerce.inventory_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENT_EXCHANGE = "event-exchange";
    public static final String INVENTORY_QUEUE = "inventory-service.queue";
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCELLED = "OrderCancelled";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue inventoryQueue() {
        return new Queue(INVENTORY_QUEUE, true);
    }

    @Bean
    public Binding orderCreatedBinding(Queue inventoryQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(inventoryQueue).to(eventExchange).with(ORDER_CREATED);
    }

    @Bean
    public Binding orderConfirmedBinding(Queue inventoryQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(inventoryQueue).to(eventExchange).with(ORDER_CONFIRMED);
    }

    @Bean
    public Binding orderCancelledBinding(Queue inventoryQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(inventoryQueue).to(eventExchange).with(ORDER_CANCELLED);
    }
}
