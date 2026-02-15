package com.example.jutjubic.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    private static final String DLQ_SUFFIX = ".dlq";
    private static final String DLX_SUFFIX = ".dlx";
    private static final int MESSAGE_TTL_MS = 60_000;
    private static final int PREFETCH_COUNT = 1;
    public static final String UPLOAD_EXCHANGE = "upload.exchange";
    public static final String UPLOAD_QUEUE_JSON = "upload.queue.json";
    public static final String UPLOAD_QUEUE_PB = "upload.queue.pb";
    public static final String UPLOAD_ROUTING_JSON = "upload.json";
    public static final String UPLOAD_ROUTING_PB = "upload.pb";

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

    @Bean
    public DirectExchange uploadExchange() {
        return new DirectExchange(UPLOAD_EXCHANGE);
    }

    @Bean
    public Queue uploadQueueJson() {
        return QueueBuilder.durable(UPLOAD_QUEUE_JSON).build();
    }

    @Bean
    public Queue uploadQueuePb() {
        return QueueBuilder.durable(UPLOAD_QUEUE_PB).build();
    }

    @Bean
    public Binding uploadJsonBinding(Queue uploadQueueJson, DirectExchange uploadExchange) {
        return BindingBuilder.bind(uploadQueueJson)
                .to(uploadExchange)
                .with(UPLOAD_ROUTING_JSON);
    }

    @Bean
    public Binding uploadPbBinding(Queue uploadQueuePb, DirectExchange uploadExchange) {
        return BindingBuilder.bind(uploadQueuePb)
                .to(uploadExchange)
                .with(UPLOAD_ROUTING_PB);
    }

    /**
     * RabbitAdmin za eksplicitno kreiranje exchange-a, queue-ova i binding-a
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    /**
     * Eksplicitno deklariši sve potrebne komponente pri startu
     */
    @Bean
    public Boolean declareUploadInfrastructure(RabbitAdmin rabbitAdmin,
                                                DirectExchange uploadExchange,
                                                Queue uploadQueueJson,
                                                Queue uploadQueuePb,
                                                Binding uploadJsonBinding,
                                                Binding uploadPbBinding) {
        try {
            rabbitAdmin.declareExchange(uploadExchange);
            logger.info("Declared exchange: {}", UPLOAD_EXCHANGE);

            rabbitAdmin.declareQueue(uploadQueueJson);
            logger.info("Declared queue: {}", UPLOAD_QUEUE_JSON);

            rabbitAdmin.declareQueue(uploadQueuePb);
            logger.info("Declared queue: {}", UPLOAD_QUEUE_PB);

            rabbitAdmin.declareBinding(uploadJsonBinding);
            logger.info("Declared binding: {} -> {} with key {}", UPLOAD_QUEUE_JSON, UPLOAD_EXCHANGE, UPLOAD_ROUTING_JSON);

            rabbitAdmin.declareBinding(uploadPbBinding);
            logger.info("Declared binding: {} -> {} with key {}", UPLOAD_QUEUE_PB, UPLOAD_EXCHANGE, UPLOAD_ROUTING_PB);

            return true;
        } catch (Exception e) {
            logger.error("Failed to declare upload infrastructure: {}", e.getMessage(), e);
            return false;
        }
    }
}
