package com.brokerx.market_service.infrastructure.config;

import com.brokerx.market_service.application.service.MarketSubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket event interceptor to handle connection, disconnection, subscription, and unsubscription events
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketEventInterceptor implements WebSocketMessageBrokerConfigurer {

    private final MarketSubscriptionService subscriptionService;

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    String sessionId = accessor.getSessionId();

                    switch (accessor.getCommand()) {
                        case CONNECT:
                            handleConnect(sessionId, accessor);
                            break;
                        case DISCONNECT:
                            handleDisconnect(sessionId);
                            break;
                        case SUBSCRIBE:
                            handleSubscribe(sessionId, accessor);
                            break;
                        case UNSUBSCRIBE:
                            handleUnsubscribe(sessionId, accessor);
                            break;
                        default:
                            // Other commands - no special action needed
                            break;
                    }
                }

                return message;
            }
        });
    }

    /**
     * Handles WebSocket connection events
     */
    private void handleConnect(String sessionId, StompHeaderAccessor accessor) {
        String userId = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        log.info("WebSocket connected - Session: {}, User: {}", sessionId, userId);

        // Connection statistics
        logConnectionStats();
    }

    /**
     * Handles WebSocket disconnection events
     */
    private void handleDisconnect(String sessionId) {
        log.info("WebSocket disconnected - Session: {}", sessionId);

        try {
            // Clean up subscription on disconnect
            subscriptionService.removeSubscription(sessionId);
            log.info("Cleaned up subscription for disconnected session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error cleaning up subscription for session: {}", sessionId, e);
        }

        // Connection statistics
        logConnectionStats();
    }

    /**
     * Handles subscription events to topics
     */
    private void handleSubscribe(String sessionId, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        log.debug("Session {} subscribed to: {}", sessionId, destination);

        // Update subscription activity
        subscriptionService.updateActivity(sessionId);
    }

    /**
     * Handles unsubscription events from topics
     */
    private void handleUnsubscribe(String sessionId, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        log.debug("Session {} unsubscribed from: {}", sessionId, destination);
    }

    /**
     * Handles unsubscription events from topics
     */
    private void logConnectionStats() {
        int activeSubscriptions = subscriptionService.getActiveSubscriptionsCount();
        log.info("Active subscriptions: {}", activeSubscriptions);
    }
}