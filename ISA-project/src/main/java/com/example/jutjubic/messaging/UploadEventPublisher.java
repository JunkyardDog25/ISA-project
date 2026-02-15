package com.example.jutjubic.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.jutjubic.config.RabbitMQConfig;
import com.example.jutjubic.dto.MqBenchResult;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.proto.UploadEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class UploadEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(UploadEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishBothAfterCommit(Video v) {
        logger.info("publishBothAfterCommit called for video: {}", v.getId());

        // Extract data while still in transaction to avoid LazyInitializationException
        final UUID videoId = v.getId();
        final String title = v.getTitle();
        final long fileSize = v.getFileSize();
        final String authorUsername = (v.getCreator() != null) ? v.getCreator().getUsername() : null;
        final LocalDateTime createdAt = v.getCreatedAt();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            logger.info("Transaction is active - registering afterCommit callback");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logger.info("afterCommit callback triggered for video: {}", videoId);
                    publishBothWithData(videoId, title, fileSize, authorUsername, createdAt);
                }
            });
        } else {
            logger.warn("No active transaction - publishing immediately");
            publishBothWithData(videoId, title, fileSize, authorUsername, createdAt);
        }
    }

    private void publishBothWithData(UUID videoId, String title, long fileSize, String authorUsername, LocalDateTime createdAt) {
        logger.info("publishBothWithData called for video: {}", videoId);
        try {
            publishJsonWithData(videoId, title, fileSize, authorUsername, createdAt);
            logger.info("JSON message published successfully for video: {}", videoId);
            publishProtobufWithData(videoId, title, fileSize, authorUsername, createdAt);
            logger.info("Protobuf message published successfully for video: {}", videoId);
            logger.info("Published upload event for video: {}", videoId);
        } catch (Exception e) {
            logger.warn("Failed to publish upload event to RabbitMQ (server may be unavailable): {}", e.getMessage(), e);
        }
    }

    private void publishJsonWithData(UUID videoId, String title, long fileSize, String authorUsername, LocalDateTime createdAt) {
        UploadEventJson json = new UploadEventJson(videoId, title, fileSize, authorUsername, createdAt);

        try {
            byte[] jsonPayload = objectMapper.writeValueAsBytes(json);

            MessageProperties props = new MessageProperties();
            props.setContentType("application/json");
            props.setContentEncoding("UTF-8");

            rabbitTemplate.send(
                    RabbitMQConfig.UPLOAD_EXCHANGE,
                    RabbitMQConfig.UPLOAD_ROUTING_JSON,
                    new Message(jsonPayload, props)
            );
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failed", e);
        }
    }

    private void publishProtobufWithData(UUID videoId, String title, long fileSize, String authorUsername, LocalDateTime createdAt) {
        String createdAtStr = (createdAt != null) ? createdAt.toString() : "";

        UploadEvent pb = UploadEvent.newBuilder()
                .setVideoId(videoId != null ? videoId.getMostSignificantBits() : 0L)
                .setTitle(title != null ? title : "")
                .setSizeMB(fileSize)
                .setAuthorUsername(authorUsername != null ? authorUsername : "")
                .setCreatedAt(createdAtStr)
                .build();

        byte[] payload = pb.toByteArray();

        MessageProperties props = new MessageProperties();
        props.setContentType("application/x-protobuf");
        props.setContentEncoding("binary");

        rabbitTemplate.send(
                RabbitMQConfig.UPLOAD_EXCHANGE,
                RabbitMQConfig.UPLOAD_ROUTING_PB,
                new Message(payload, props)
        );
    }

    public UploadEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishBoth(Video savedVideo) {
        logger.info("publishBoth called for video: {}", savedVideo.getId());
        try {
            publishJson(savedVideo);
            logger.info("JSON message published successfully for video: {}", savedVideo.getId());
            publishProtobuf(savedVideo);
            logger.info("Protobuf message published successfully for video: {}", savedVideo.getId());
            logger.info("Published upload event for video: {}", savedVideo.getId());
        } catch (Exception e) {
            logger.warn("Failed to publish upload event to RabbitMQ (server may be unavailable): {}", e.getMessage(), e);
            // Don't throw - allow video creation to succeed even if RabbitMQ is down
        }
    }

    public void publishJson(Video v) {
        UploadEventJson json = new UploadEventJson(
                v.getId(),
                v.getTitle(),
                v.getFileSize(),
                v.getCreator() != null ? v.getCreator().getUsername() : null,
                v.getCreatedAt()
        );

        try {
            byte[] jsonPayload = objectMapper.writeValueAsBytes(json);

            MessageProperties props = new MessageProperties();
            props.setContentType("application/json");
            props.setContentEncoding("UTF-8");

            rabbitTemplate.send(
                    RabbitMQConfig.UPLOAD_EXCHANGE,
                    RabbitMQConfig.UPLOAD_ROUTING_JSON,
                    new Message(jsonPayload, props)
            );
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failed", e);
        }
    }

    public void publishProtobuf(Video v) {
        String createdAt = (v.getCreatedAt() != null) ? v.getCreatedAt().toString() : "";

        UploadEvent pb = UploadEvent.newBuilder()
                .setVideoId(v.getId() != null ? v.getId().getMostSignificantBits() : 0L)
                .setTitle(v.getTitle() != null ? v.getTitle() : "")
                .setSizeMB(v.getFileSize())
                .setAuthorUsername(
                        v.getCreator() != null && v.getCreator().getUsername() != null ? v.getCreator().getUsername() : ""
                )
                .setCreatedAt(createdAt)
                .build();

        byte[] payload = pb.toByteArray();

        MessageProperties props = new MessageProperties();
        props.setContentType("application/x-protobuf");
        props.setContentEncoding("binary");

        rabbitTemplate.send(
                RabbitMQConfig.UPLOAD_EXCHANGE,
                RabbitMQConfig.UPLOAD_ROUTING_PB,
                new Message(payload, props)
        );
    }

    public record UploadEventJson(
            java.util.UUID videoId,
            String title,
            Long sizeMB,
            String authorUsername,
            LocalDateTime createdAt
    ) {}

    public MqBenchResult benchmark(int count) {

        long jsonSerNanos = 0;
        long pbSerNanos = 0;

        long jsonBytesSum = 0;
        long pbBytesSum = 0;

        MessageProperties jsonProps = new MessageProperties();
        jsonProps.setContentType("application/json");
        jsonProps.setContentEncoding("UTF-8");

        for (int i = 0; i < count; i++) {

            UUID videoId = UUID.randomUUID();
            String title = "bench-" + i;
            long sizeMb = 35L + (i % 10);
            String author = "e14nemanjaradic";
            LocalDateTime createdAt = LocalDateTime.now();


            UploadEventJson jsonObj = new UploadEventJson(videoId, title, sizeMb, author, createdAt);

            long t1 = System.nanoTime();
            byte[] jsonPayload;
            try {
                jsonPayload = objectMapper.writeValueAsBytes(jsonObj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            long t2 = System.nanoTime();

            jsonSerNanos += (t2 - t1);
            jsonBytesSum += jsonPayload.length;


            rabbitTemplate.send(
                    RabbitMQConfig.UPLOAD_EXCHANGE,
                    RabbitMQConfig.UPLOAD_ROUTING_JSON,
                    new Message(jsonPayload, jsonProps)
            );


            UploadEvent pb = UploadEvent.newBuilder()
                    .setVideoId(videoId.getMostSignificantBits())
                    .setTitle(title)
                    .setSizeMB(sizeMb)
                    .setAuthorUsername(author)
                    .setCreatedAt(createdAt.toString())
                    .build();

            long p1 = System.nanoTime();
            byte[] pbPayload = pb.toByteArray();
            long p2 = System.nanoTime();

            pbSerNanos += (p2 - p1);
            pbBytesSum += pbPayload.length;

            publishProtobufBytes(pbPayload);
        }

        double avgJsonMicros = (jsonSerNanos / 1000.0) / count;
        double avgPbMicros = (pbSerNanos / 1000.0) / count;

        return new MqBenchResult(
                count,
                avgJsonMicros,
                avgPbMicros,
                (double) jsonBytesSum / count,
                (double) pbBytesSum / count
        );
    }

    private void publishProtobufBytes(byte[] payload) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/x-protobuf");
        props.setContentEncoding("binary");

        rabbitTemplate.send(
                RabbitMQConfig.UPLOAD_EXCHANGE,
                RabbitMQConfig.UPLOAD_ROUTING_PB,
                new Message(payload, props)
        );
    }

}
