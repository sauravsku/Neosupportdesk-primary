package com.centneo.fintech.supportDeskSvc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Client connects to this endpoint (wss or ws)
        registry.addEndpoint("/ws-counts")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // fallback for older browsers
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Clients subscribe to /topic/*
        registry.enableSimpleBroker("/topic");
        // Messages sent from client go to /app/*
        registry.setApplicationDestinationPrefixes("/app");
    }
}
