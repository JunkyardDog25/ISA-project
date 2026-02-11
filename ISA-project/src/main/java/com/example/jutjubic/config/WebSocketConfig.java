package com.example.jutjubic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket konfiguracija za real-time chat funkcionalnost.
 * Koristi STOMP protokol preko WebSocket-a za razmenu poruka.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Omogućava simple broker za slanje poruka ka klijentima
        // /topic - za broadcast poruke (chat sobe za video)
        config.enableSimpleBroker("/topic");

        // Prefiks za poruke koje dolaze od klijenata
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint na koji se klijenti konektuju
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:8080")
                .withSockJS(); // Fallback za browsere koji ne podržavaju WebSocket
    }
}
