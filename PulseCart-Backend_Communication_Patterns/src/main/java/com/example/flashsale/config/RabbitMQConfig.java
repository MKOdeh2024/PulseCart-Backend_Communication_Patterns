package com.example.flashsale.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Flash Sale System
 * Sets up queues, exchanges, and bindings for async purchase processing
 */
@Configuration
@EnableRabbit
@Slf4j
public class RabbitMQConfig {

    // Queue names
    public static final String PURCHASE_QUEUE = "flashsale.purchase.queue";
    public static final String PURCHASE_DLQ = "flashsale.purchase.dlq";
    public static final String STOCK_UPDATE_QUEUE = "flashsale.stock.update.queue";

    // Exchange names
    public static final String PURCHASE_EXCHANGE = "flashsale.purchase.exchange";
    public static final String STOCK_UPDATE_EXCHANGE = "flashsale.stock.update.exchange";
    public static final String DLX_EXCHANGE = "flashsale.dlx.exchange";

    // Routing keys
    public static final String PURCHASE_ROUTING_KEY = "purchase.process";
    public static final String STOCK_UPDATE_ROUTING_KEY = "stock.update";
    public static final String DLQ_ROUTING_KEY = "purchase.failed";

    @Value("${spring.rabbitmq.listener.simple.concurrency:5}")
    private int concurrency;

    @Value("${spring.rabbitmq.listener.simple.max-concurrency:20}")
    private int maxConcurrency;

    /**
     * Purchase Queue - Main queue for purchase requests
     * With Dead Letter Queue for failed messages
     */
    @Bean
    public Queue purchaseQueue() {
        return QueueBuilder.durable(PURCHASE_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
            .withArgument("x-message-ttl", 60000) // 60 seconds TTL
            .build();
    }

    /**
     * Dead Letter Queue for failed purchase messages
     */
    @Bean
    public Queue purchaseDeadLetterQueue() {
        return QueueBuilder.durable(PURCHASE_DLQ).build();
    }

    /**
     * Stock Update Queue - For broadcasting stock changes
     */
    @Bean
    public Queue stockUpdateQueue() {
        return QueueBuilder.durable(STOCK_UPDATE_QUEUE).build();
    }

    /**
     * Purchase Exchange - Direct exchange for purchase routing
     */
    @Bean
    public DirectExchange purchaseExchange() {
        return new DirectExchange(PURCHASE_EXCHANGE, true, false);
    }

    /**
     * Stock Update Exchange - Topic exchange for stock updates
     */
    @Bean
    public TopicExchange stockUpdateExchange() {
        return new TopicExchange(STOCK_UPDATE_EXCHANGE, true, false);
    }

    /**
     * Dead Letter Exchange
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    /**
     * Bind Purchase Queue to Purchase Exchange
     */
    @Bean
    public Binding purchaseBinding() {
        return BindingBuilder
            .bind(purchaseQueue())
            .to(purchaseExchange())
            .with(PURCHASE_ROUTING_KEY);
    }

    /**
     * Bind Dead Letter Queue to DLX
     */
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder
            .bind(purchaseDeadLetterQueue())
            .to(deadLetterExchange())
            .with(DLQ_ROUTING_KEY);
    }

    /**
     * Bind Stock Update Queue to Stock Update Exchange
     */
    @Bean
    public Binding stockUpdateBinding() {
        return BindingBuilder
            .bind(stockUpdateQueue())
            .to(stockUpdateExchange())
            .with(STOCK_UPDATE_ROUTING_KEY);
    }

    /**
     * JSON Message Converter for RabbitMQ
     * Allows automatic serialization/deserialization of Java objects
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON message converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        // Enable mandatory to handle unroutable messages
        template.setMandatory(true);

        // Return callback for unroutable messages
        template.setReturnsCallback(returned -> {
            log.error("Message returned: exchange={}, routingKey={}, message={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getMessage());
        });

        // Confirm callback for publisher confirms
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Message confirmed: {}", correlationData);
            } else {
                log.error("Message not confirmed: {}, cause: {}", correlationData, cause);
            }
        });

        return template;
    }

    /**
     * Listener Container Factory with JSON converter
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setPrefetchCount(10);

        // Enable auto-acknowledgment (can be changed to manual for more control)
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);

        // Error handler
        factory.setErrorHandler(throwable -> {
            log.error("Error in message listener: ", throwable);
        });

        return factory;
    }

    /**
     * Initialize RabbitMQ on startup
     */
    @Bean
    public RabbitMQInitializer rabbitMQInitializer() {
        return new RabbitMQInitializer();
    }

    /**
     * Inner class to log RabbitMQ initialization
     */
    public static class RabbitMQInitializer {
        public RabbitMQInitializer() {
            log.info("===================================================");
            log.info("RabbitMQ Configuration Initialized");
            log.info("Purchase Queue: {}", PURCHASE_QUEUE);
            log.info("Stock Update Queue: {}", STOCK_UPDATE_QUEUE);
            log.info("Dead Letter Queue: {}", PURCHASE_DLQ);
            log.info("===================================================");
        }
    }
}
