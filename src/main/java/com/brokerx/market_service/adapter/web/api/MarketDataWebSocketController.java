package com.brokerx.market_service.adapter.web.api;

import com.brokerx.market_service.adapter.web.dto.SubscriptionRequestDto;
import com.brokerx.market_service.application.port.in.BroadcastMarketDataUseCase;
import com.brokerx.market_service.application.port.in.GetMarketDataUseCase;
import com.brokerx.market_service.application.port.in.ManageSubscriptionUseCase;
import com.brokerx.market_service.application.port.in.SubscribeToMarketDataUseCase;

import java.util.HashSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * WebSocket Controller for managing market data subscriptions
 * Uses hexagonal architecture - depends on use case ports
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MarketDataWebSocketController {

    private final SubscribeToMarketDataUseCase subscribeToMarketDataUseCase;
    private final ManageSubscriptionUseCase manageSubscriptionUseCase;
    private final GetMarketDataUseCase getMarketDataUseCase;
    private final BroadcastMarketDataUseCase broadcastMarketDataUseCase;

    /**
     * Handles market data subscription requests
     */
    @MessageMapping("/market/subscribe")
    public void subscribe(@Payload SubscriptionRequestDto request,
            SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) {

        String sessionId = headerAccessor.getSessionId();
        String userId = authentication.getPrincipal().toString();

        log.info("Received subscription request from session: {} for symbols: {}",
                sessionId, request.getSymbols());

        try {
            // Validate input data
            if (request.getSymbols() == null || request.getSymbols().isEmpty()) {
                broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "No symbols provided for subscription");
                return;
            }

            // Normalize and validate symbols
            Set<String> normalizedSymbols = new HashSet<>();
            for (String symbol : request.getSymbols()) {
                String upperSymbol = symbol.toUpperCase();
                if (getMarketDataUseCase.isSymbolAvailable(upperSymbol)) {
                    normalizedSymbols.add(upperSymbol);
                } else {
                    log.warn("Symbol {} is not available", upperSymbol);
                }
            }

            if (normalizedSymbols.isEmpty()) {
                broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "None of the requested symbols are available");
                return;
            }

            // Handles the subscription action
            handleSubscriptionAction(request.getAction(), sessionId, userId, normalizedSymbols);

        } catch (Exception e) {
            log.error("Error processing subscription request from session: {}", sessionId, e);
            broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "Internal error processing subscription");
        }
    }

    /**
     * Handles subscriptions to specific market topics
     */
    @SubscribeMapping("/topic/market/{symbol}")
    public void subscribeToSymbol(SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) {

        String sessionId = headerAccessor.getSessionId();
        String userId = authentication.getPrincipal().toString();

        log.info("Client {} subscribed to market topic via session: {}", userId, sessionId);

        // Updates the subscription activity if it exists
        manageSubscriptionUseCase.updateActivity(sessionId);
    }

    /**
     * Handles connections to the global market topic
     */
    @SubscribeMapping("/topic/market/all")
    public void subscribeToAll(SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) {

        String sessionId = headerAccessor.getSessionId();
        String userId = authentication.getPrincipal().toString();

        log.info("Client {} subscribed to ALL market data via session: {}", userId, sessionId);

        // Updates the subscription activity if it exists
        manageSubscriptionUseCase.updateActivity(sessionId);
    }

    /**
     * Handles the different subscription actions
     */
    private void handleSubscriptionAction(String action, String sessionId, String userId, Set<String> symbols) {
        if (action == null) {
            action = "subscribe"; // Default action
        }

        switch (action.toLowerCase()) {
            case "subscribe":
                handleSubscribe(sessionId, userId, symbols);
                break;
            case "unsubscribe":
                handleUnsubscribe(sessionId, symbols);
                break;
            case "add":
                handleAddSymbols(sessionId, symbols);
                break;
            case "remove":
                handleRemoveSymbols(sessionId, symbols);
                break;
            default:
                broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "Unknown action: " + action);
        }
    }

    /**
     * Handles subscriptions to specific symbols
     */
    private void handleSubscribe(String sessionId, String userId, Set<String> symbols) {
        try {
            subscribeToMarketDataUseCase.subscribe(sessionId, userId, symbols);
            broadcastMarketDataUseCase.sendSubscriptionSuccess(sessionId, symbols);

            log.info("Successfully subscribed session {} to symbols: {}", sessionId, symbols);

        } catch (Exception e) {
            log.error("Error creating subscription for session: {}", sessionId, e);
            broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "Failed to create subscription");
        }
    }

    /**
     * Handles unsubscriptions from symbols
     */
    private void handleUnsubscribe(String sessionId, Set<String> symbols) {
        try {
            if (symbols.isEmpty()) {
                // Complete unsubscription
                manageSubscriptionUseCase.removeSubscription(sessionId);
                broadcastMarketDataUseCase.sendSubscriptionSuccess(sessionId, Set.of("all"));
                log.info("Unsubscribed session {} from all symbols", sessionId);
            } else {
                // Partial unsubscription
                subscribeToMarketDataUseCase.removeSymbols(sessionId, symbols);
                broadcastMarketDataUseCase.sendSubscriptionSuccess(sessionId, symbols);
                log.info("Unsubscribed session {} from symbols: {}", sessionId, symbols);
            }
        } catch (Exception e) {
            log.error("Error unsubscribing session: {}", sessionId, e);
            broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "Failed to unsubscribe");
        }
    }

    /**
     * Handles adding symbols to an existing subscription
     */
    private void handleAddSymbols(String sessionId, Set<String> symbols) {
        try {
            subscribeToMarketDataUseCase.addSymbols(sessionId, symbols);
            broadcastMarketDataUseCase.sendSubscriptionSuccess(sessionId, symbols);
            log.info("Added symbols {} to subscription {}", symbols, sessionId);
        } catch (Exception e) {
            log.error("Error adding symbols to subscription {}", sessionId, e);
            broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "Failed to add symbols");
        }
    }

    /**
     * Handles removing symbols from an existing subscription
     */
    private void handleRemoveSymbols(String sessionId, Set<String> symbols) {
        try {
            subscribeToMarketDataUseCase.removeSymbols(sessionId, symbols);
            broadcastMarketDataUseCase.sendSubscriptionSuccess(sessionId, symbols);
            log.info("Removed symbols {} from subscription {}", symbols, sessionId);
        } catch (Exception e) {
            log.error("Error removing symbols from subscription {}", sessionId, e);
            broadcastMarketDataUseCase.sendSubscriptionError(sessionId, "Failed to remove symbols");
        }
    }
}