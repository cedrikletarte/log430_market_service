package com.brokerx.market_service.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* WebSocketEventInterceptor intercepts WebSocket events to handle authentication */
@Slf4j
@Component
public class WebSocketEventInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Track active sessions (sessionId -> userId)
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    /* Intercepts messages to handle authentication and events */
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

    /* Handles JWT authentication for WebSocket connections */
    private boolean handleAuthentication(StompHeaderAccessor accessor) {
        try {
            String authToken = accessor.getFirstNativeHeader("Authorization");
            
            if (authToken == null || !authToken.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header");
                return false;
            }

            String token = authToken.substring(7);
            
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

    /* Handles WebSocket connection events */
    private void handleConnect(String sessionId, StompHeaderAccessor accessor) {
        String userId = "anonymous";
        var user = accessor.getUser();
        if (user != null) {
            userId = user.getName();
        }
        log.info("WebSocket connected - Session: {}, User: {}", sessionId, userId);

        // Store active session
        activeSessions.put(sessionId, userId);

        log.info("Active WebSocket connections: {}", activeSessions.size());
    }

    /* Handles WebSocket disconnection events */
    private void handleDisconnect(String sessionId) {
        String userId = activeSessions.remove(sessionId);
        log.info("WebSocket disconnected - Session: {}, User: {}", sessionId, userId);

        log.info("Active WebSocket connections: {}", activeSessions.size());
    }

    /* Handles subscription events to topics */
    private void handleSubscribe(String sessionId, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        String userId = activeSessions.get(sessionId);
        log.debug("Session {} (User: {}) subscribed to: {}", sessionId, userId, destination);
    }

    /* Handles unsubscription events from topics */
    private void handleUnsubscribe(String sessionId, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        String userId = activeSessions.get(sessionId);
        log.debug("Session {} (User: {}) unsubscribed from: {}", sessionId, userId, destination);
    }
}