package com.example.jutjubic.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time chat functionality.
 * Uses STOMP protocol over WebSocket for message exchange.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String BROKER_DESTINATION = "/topic";
    private static final String APP_DESTINATION_PREFIX = "/app";
    private static final String WEBSOCKET_ENDPOINT = "/ws";
    private static final String[] ALLOWED_ORIGINS = {
            "http://localhost:5173",
            "http://localhost:8080"
    };

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker(BROKER_DESTINATION);
        config.setApplicationDestinationPrefixes(APP_DESTINATION_PREFIX);
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint(WEBSOCKET_ENDPOINT)
                .setAllowedOrigins(ALLOWED_ORIGINS)
                .withSockJS();
    }
}
