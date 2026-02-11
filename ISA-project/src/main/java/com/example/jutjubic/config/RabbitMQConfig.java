package com.example.jutjubic.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private static final String DLQ_SUFFIX = ".dlq";
    private static final String DLX_SUFFIX = ".dlx";
    private static final int MESSAGE_TTL_MS = 60_000;
    private static final int PREFETCH_COUNT = 1;

    @Value("${transcoding.queue.name}")
    private String queueName;

    @Value("${transcoding.exchange.name}")
    private String exchangeName;

    @Value("${transcoding.routing.key}")
    private String routingKey;

    // ==================== Queues ====================

    @Bean
    public Queue transcodingQueue() {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(exchangeName + DLX_SUFFIX)
                .deadLetterRoutingKey(routingKey + DLQ_SUFFIX)
                .build();
    }

    @Bean
    public Queue transcodingDeadLetterQueue() {
        return QueueBuilder.durable(queueName + DLQ_SUFFIX)
                .deadLetterExchange(exchangeName)
                .deadLetterRoutingKey(routingKey)
                .ttl(MESSAGE_TTL_MS)
                .build();
    }

    // ==================== Exchanges ====================

    @Bean
    public DirectExchange transcodingExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public DirectExchange transcodingDeadLetterExchange() {
        return new DirectExchange(exchangeName + DLX_SUFFIX);
    }

    // ==================== Bindings ====================

    @Bean
    public Binding transcodingBinding(Queue transcodingQueue, DirectExchange transcodingExchange) {
        return BindingBuilder
                .bind(transcodingQueue)
                .to(transcodingExchange)
                .with(routingKey);
    }

    @Bean
    public Binding deadLetterBinding(Queue transcodingDeadLetterQueue, DirectExchange transcodingDeadLetterExchange) {
        return BindingBuilder
                .bind(transcodingDeadLetterQueue)
                .to(transcodingDeadLetterExchange)
                .with(routingKey + DLQ_SUFFIX);
    }

    // ==================== Messaging ====================

    @Bean
    @SuppressWarnings("removal")
    public MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(PREFETCH_COUNT);
        return factory;
    }
}
