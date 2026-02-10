package com.example.jutjubic.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {

    @Value("${transcoding.queue.name}")
    private String queueName;

    @Value("${transcoding.exchange.name}")
    private String exchangeName;

    @Value("${transcoding.routing.key}")
    private String routingKey;

    // Dead Letter Queue configuration
    private static final String DLQ_SUFFIX = ".dlq";
    private static final String DLX_SUFFIX = ".dlx";
    private static final int MESSAGE_TTL = 60000; // 60 seconds before retry


    @Bean
    public Queue transcodingQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", exchangeName + DLX_SUFFIX)
                .withArgument("x-dead-letter-routing-key", routingKey + DLQ_SUFFIX)
                .build();
    }

    @Bean
    public Queue transcodingDeadLetterQueue() {
        return QueueBuilder.durable(queueName + DLQ_SUFFIX)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .withArgument("x-message-ttl", MESSAGE_TTL)
                .build();
    }

    /**
     * Creates a direct exchange for transcoding messages.
     */
    @Bean
    public DirectExchange transcodingExchange() {
        return new DirectExchange(exchangeName);
    }

    /**
     * Creates Dead Letter Exchange for failed messages.
     */
    @Bean
    public DirectExchange transcodingDeadLetterExchange() {
        return new DirectExchange(exchangeName + DLX_SUFFIX);
    }

    /**
     * Binds the queue to the exchange with the routing key.
     */
    @Bean
    public Binding transcodingBinding(Queue transcodingQueue, DirectExchange transcodingExchange) {
        return BindingBuilder
                .bind(transcodingQueue)
                .to(transcodingExchange)
                .with(routingKey);
    }

    /**
     * Binds the Dead Letter Queue to the Dead Letter Exchange.
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(transcodingDeadLetterQueue())
                .to(transcodingDeadLetterExchange())
                .with(routingKey + DLQ_SUFFIX);
    }

    /**
     * JSON message converter for serializing/deserializing messages.
     */
    @Bean
    @java.lang.SuppressWarnings("removal")
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON message converter.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    /**
     * Listener container factory with manual acknowledgment.
     * This ensures that a message is only acknowledged after successful processing,
     * preventing duplicate delivery to multiple consumers.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        // Manual acknowledgment to prevent duplicate processing
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // Prefetch 1 message at a time to ensure fair distribution among consumers
        factory.setPrefetchCount(1);
        return factory;
    }
}





