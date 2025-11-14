package com.brokerx.market_service.adapter.web.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * Simplified WebSocket Controller for market data
 * Clients subscribe directly to STOMP topics - Spring handles the rest
 */
@Slf4j
@Controller
public class MarketDataWebSocketController {

    /**
     * Handles subscriptions to specific market topics
     * Spring WebSocket automatically manages the subscription
     */
    @SubscribeMapping("/topic/market/{symbol}")
    public void subscribeToSymbol(SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) {

        String sessionId = headerAccessor.getSessionId();
        String userId = authentication != null ? authentication.getPrincipal().toString() : "anonymous";

        log.info("Client {} subscribed to market topic via session: {}", userId, sessionId);
    }

    /**
     * Handles subscriptions to the global market topic
     * Spring WebSocket automatically manages the subscription
     */
    @SubscribeMapping("/topic/market/all")
    public void subscribeToAll(SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) {

        String sessionId = headerAccessor.getSessionId();
        String userId = authentication != null ? authentication.getPrincipal().toString() : "anonymous";

        log.info("Client {} subscribed to ALL market data via session: {}", userId, sessionId);
    }
}