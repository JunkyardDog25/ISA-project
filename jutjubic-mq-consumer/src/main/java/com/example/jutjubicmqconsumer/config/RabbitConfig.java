package com.example.jutjubicmqconsumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

@Configuration
public class RabbitConfig {
    public static final String UPLOAD_QUEUE_JSON = "upload.queue.json";
    public static final String UPLOAD_QUEUE_PB   = "upload.queue.pb";

    @Bean
    public Queue uploadQueueJson() {
        return QueueBuilder.durable(UPLOAD_QUEUE_JSON).build();
    }

    @Bean
    public Queue uploadQueuePb() {
        return QueueBuilder.durable(UPLOAD_QUEUE_PB).build();
    }
    @Bean
    public com.example.jutjubicmqconsumer.stats.MqStats mqStats() {
        return new com.example.jutjubicmqconsumer.stats.MqStats();
    }
}