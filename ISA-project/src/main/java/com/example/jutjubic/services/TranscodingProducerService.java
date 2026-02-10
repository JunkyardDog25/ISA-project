package com.example.jutjubic.services;

import com.example.jutjubic.dto.TranscodingJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service responsible for sending video transcoding jobs to the RabbitMQ queue.
 * This is the producer side of the transcoding pipeline.
 */
@Service
public class TranscodingProducerService {

    private static final Logger logger = LoggerFactory.getLogger(TranscodingProducerService.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${transcoding.exchange.name}")
    private String exchangeName;

    @Value("${transcoding.routing.key}")
    private String routingKey;

    @Value("${transcoding.output.directory}")
    private String outputDirectory;

    // Predefined transcoding parameters from application.properties
    @Value("${transcoding.params.video-codec}")
    private String videoCodec;

    @Value("${transcoding.params.audio-codec}")
    private String audioCodec;

    @Value("${transcoding.params.resolution}")
    private String resolution;

    @Value("${transcoding.params.video-bitrate}")
    private Long videoBitrate;

    @Value("${transcoding.params.audio-bitrate}")
    private Long audioBitrate;

    @Value("${transcoding.params.format}")
    private String format;

    public TranscodingProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Sends a transcoding job to the message queue.
     * Video ID koji se transkodira i izvori direktorijum /media/videos/
     */
    public void sendTranscodingJob(UUID videoId, String sourcePath) {
        // Generate output path for transcoded video
        String outputFileName = "transcoded_" + videoId.toString() + "." + format;
        String outputPath = Paths.get(outputDirectory, outputFileName).toString();

        // Create transcoding job message with predefined parameters
        TranscodingJobMessage message = new TranscodingJobMessage(
                videoId,
                sourcePath,
                outputPath,
                videoCodec,
                audioCodec,
                resolution,
                videoBitrate,
                audioBitrate,
                format
        );

        logger.info("Sending transcoding job to queue for video: {}", videoId);
        logger.debug("Transcoding job details: {}", message);

        // Send message to exchange with routing key
        rabbitTemplate.convertAndSend(exchangeName, routingKey, message);

        logger.info("Transcoding job sent successfully for video: {}", videoId);
    }

}

