package com.example.jutjubic.services;

import com.example.jutjubic.dto.TranscodingJobMessage;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.VideoRepository;
import com.rabbitmq.client.Channel;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 Servis je zadužen za preuzimanje poslova za video transkodiranje iz RabbitMQ reda poruka.
 Servis može da radi sa više potrošača istovremeno (broj se podešava u application.properties) što
 omogućava paralelnu obradu više video zapisa koristeći FFmpeg biblioteku.
 Manualno potvrđivanje poruka se koristi kako bi se osiguralo da se poruke ne gube u slučaju grešaka tokom obrade.
 */
@Service
public class TranscodingConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(TranscodingConsumerService.class);

    private final VideoRepository videoRepository;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${ffprobe.path}")
    private String ffprobePath;

    @Value("${transcoding.output.directory}")
    private String outputDirectory;

    private FFmpeg ffmpeg;
    private FFprobe ffprobe;

    public TranscodingConsumerService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    /**
     * Initialize FFmpeg and FFprobe instances after dependency injection.
     * Creates output directory if it doesn't exist.
     */
    @PostConstruct
    public void init() {
        try {
            ffmpeg = new FFmpeg(ffmpegPath);
            ffprobe = new FFprobe(ffprobePath);
            logger.info("FFmpeg initialized successfully at: {}", ffmpegPath);
            logger.info("FFprobe initialized successfully at: {}", ffprobePath);

            // Ensure output directory exists
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                logger.info("Created transcoding output directory: {}", outputDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize FFmpeg/FFprobe: {}", e.getMessage());
            logger.warn("Transcoding will not work until FFmpeg is properly configured");
        }
    }

    private static final int MAX_RETRY_COUNT = 3;

    /**
     Metoda sluša poruke iz reda koje predstavljaju poslove za video transkodiranje.
     Koristi se ručna potvrda poruka kako bi se obezbedila pouzdana obrada.
     Broj potrošača koji rade paralelno podešava se preko parametra ${transcoding.consumer.concurrency}, pri čemu su uvek aktivna najmanje dva potrošača.
     Poruke koje ne mogu uspešno da se obrade šalju se u Dead Letter Queue (DLQ) i ponovo se pokušavaju obraditi najviše MAX_RETRY_COUNT puta.
     Parametri metode:
     message – poruka sa podacima o video fajlu i parametrima za transkodiranje
     channel – RabbitMQ kanal koji se koristi za ručnu potvrdu poruka
        deliveryTag – jedinstveni identifikator poruke koji se koristi za potvrdu
     */
    @RabbitListener(
            queues = "${transcoding.queue.name}",
            concurrency = "${transcoding.consumer.concurrency}"
    )
    public void processTranscodingJob(TranscodingJobMessage message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                       @Header(name = "x-death", required = false) java.util.List<java.util.Map<String, Object>> xDeath) {
        String consumerThread = Thread.currentThread().getName();
        int retryCount = getRetryCount(xDeath);

        logger.info("[{}] Received transcoding job for video: {} (attempt {}/{})",
                consumerThread, message.getVideoId(), retryCount + 1, MAX_RETRY_COUNT);

        try {
            // Validate FFmpeg is available
            if (ffmpeg == null || ffprobe == null) {
                throw new IllegalStateException("FFmpeg is not properly initialized");
            }

            // Validate source file exists
            Path sourcePath = Paths.get(message.getSourcePath());
            if (!Files.exists(sourcePath)) {
                throw new IOException("Source video file not found: " + message.getSourcePath());
            }

            // Perform transcoding
            transcodeVideo(message);

            // Update video entity to mark as transcoded
            updateVideoTranscodedStatus(message.getVideoId(), message.getOutputPath());

            // Acknowledge successful processing
            channel.basicAck(deliveryTag, false);
            logger.info("[{}] Successfully transcoded video: {}", consumerThread, message.getVideoId());

        } catch (Exception e) {
            logger.error("[{}] Failed to transcode video {}: {}",
                    consumerThread, message.getVideoId(), e.getMessage(), e);

            try {
                if (retryCount >= MAX_RETRY_COUNT - 1) {
                    // Max retries reached - acknowledge to prevent further processing
                    channel.basicAck(deliveryTag, false);
                    logger.error("[{}] Max retries ({}) reached for video: {}. Giving up.",
                            consumerThread, MAX_RETRY_COUNT, message.getVideoId());
                } else {
                    // Reject message - it will be sent to DLQ and retried after TTL
                    channel.basicNack(deliveryTag, false, false);
                    logger.warn("[{}] Message sent to DLQ for retry. Video: {}, Attempt: {}/{}",
                            consumerThread, message.getVideoId(), retryCount + 1, MAX_RETRY_COUNT);
                }
            } catch (IOException ioException) {
                logger.error("[{}] Failed to ack/nack message: {}", consumerThread, ioException.getMessage());
            }
        }
    }

    /**
     Izvlači broj pokušaja ponovne obrade poruke iz x-death zaglavlja.
     xDeath – lista zapisa iz x-death zaglavlja
     */
    private int getRetryCount(java.util.List<java.util.Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        try {
            // Get count from first death record
            Object count = xDeath.get(0).get("count");
            if (count instanceof Number) {
                return ((Number) count).intValue();
            }
        } catch (Exception e) {
            logger.debug("Could not parse x-death header: {}", e.getMessage());
        }
        return 0;
    }

    /**
     Izvršava stvarni proces transkodiranja videa korišćenjem FFmpeg alata.
     message – poruka sa svim parametrima potrebnim za transkodiranje
     IOException – baca se u slučaju da transkodiranje ne uspe
     */
    private void transcodeVideo(TranscodingJobMessage message) throws IOException {
        logger.info("Starting FFmpeg transcoding for: {}", message.getSourcePath());

        // Probe source video to get information
        FFmpegProbeResult probeResult = ffprobe.probe(message.getSourcePath());
        logger.debug("Source video duration: {} seconds", probeResult.getFormat().duration);

        // Parse resolution
        int width = 1280;
        int height = 720;
        if (message.getResolution() != null && message.getResolution().contains("x")) {
            String[] dimensions = message.getResolution().split("x");
            width = Integer.parseInt(dimensions[0]);
            height = Integer.parseInt(dimensions[1]);
        }

        // Build FFmpeg command
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(message.getSourcePath())
                .overrideOutputFiles(true)
                .addOutput(message.getOutputPath())
                .setFormat(message.getFormat())
                .setVideoCodec(message.getVideoCodec())
                .setVideoResolution(width, height)
                .setVideoBitRate(message.getVideoBitrate())
                .setAudioCodec(message.getAudioCodec())
                .setAudioBitRate(message.getAudioBitrate())
                .setAudioChannels(2)
                .setAudioSampleRate(44100)
                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL)
                .done();

        // Execute transcoding
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);

        logger.info("Executing FFmpeg transcoding...");
        executor.createJob(builder).run();

        logger.info("Transcoding completed successfully. Output: {}", message.getOutputPath());
    }

    /**
     Ažurira video entitet tako da označi da je video uspešno transkodiran i čuva putanju do transkodiranog video fajla.
     */
    private void updateVideoTranscodedStatus(java.util.UUID videoId, String transcodedPath) {
        Optional<Video> optionalVideo = videoRepository.findVideoById(videoId);

        if (optionalVideo.isPresent()) {
            Video video = optionalVideo.get();
            video.setTranscoded(true);
            video.setTranscodedVideoPath(transcodedPath);
            videoRepository.save(video);
            logger.info("Updated video {} transcoded status to true, path: {}", videoId, transcodedPath);
        } else {
            logger.warn("Video not found with ID: {}. Cannot update transcoded status.", videoId);
        }
    }
}




