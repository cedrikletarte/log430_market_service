package com.brokerx.market_service.infrastructure.config;

import com.brokerx.market_service.application.service.MarketSubscriptionService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * WebSocket event interceptor to handle authentication, connection, disconnection, 
 * subscription, and unsubscription events
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketEventInterceptor implements WebSocketMessageBrokerConfigurer {

    private final MarketSubscriptionService subscriptionService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    String sessionId = accessor.getSessionId();

                    // Handle authentication on CONNECT - BEFORE other processing
                    if (StompCommand.CONNECT.equals(command)) {
                        boolean authenticated = handleAuthentication(accessor);
                        if (!authenticated) {
                            log.error("Authentication failed for session: {}", sessionId);
                            return null; // Reject the message
                        }
                    }

                    // Handle other events
                    if (command != null) {
                        switch (command) {
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
                }

                return message;
            }
        });
    }

    /**
     * Handles JWT authentication for WebSocket connections
     * @return true if authentication successful, false otherwise
     */
    private boolean handleAuthentication(StompHeaderAccessor accessor) {
        try {
            String authToken = accessor.getFirstNativeHeader("Authorization");
            System.out.println("Auth Token reçu: " + authToken);
            
            if (authToken == null || !authToken.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header");
                return false;
            }

            String token = authToken.substring(7);

            System.out.println("Token reçu: " + token);
            
            byte[] keyBytes = java.util.Base64.getDecoder().decode(jwtSecret);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);

            accessor.setUser(authentication);
            
            log.info("WebSocket authenticated: userId={}, email={}, role={}", userId, email, role);
            return true;
            
        } catch (Exception e) {
            log.error("WebSocket authentication failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handles WebSocket connection events
     */
    private void handleConnect(String sessionId, StompHeaderAccessor accessor) {
        String userId = (accessor.getUser() != null) ? accessor.getUser().getName() : "anonymous";
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
     * Logs connection statistics
     */
    private void logConnectionStats() {
        int activeSubscriptions = subscriptionService.getActiveSubscriptionsCount();
        log.info("Active subscriptions: {}", activeSubscriptions);
    }
}