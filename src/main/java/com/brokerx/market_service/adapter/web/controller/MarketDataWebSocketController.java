package com.brokerx.market_service.adapter.web.controller;

import com.brokerx.market_service.adapter.web.dto.SubscriptionRequestDto;
import com.brokerx.market_service.application.service.MarketDataBroadcastService;
import com.brokerx.market_service.application.service.MarketDataSimulationService;
import com.brokerx.market_service.application.service.MarketSubscriptionService;
import com.brokerx.market_service.domain.model.MarketSubscription;

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
 * Websocket Controller for managing market data subscriptions
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MarketDataWebSocketController {

    private final MarketSubscriptionService subscriptionService;
    private final MarketDataSimulationService simulationService;
    private final MarketDataBroadcastService broadcastService;

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
                broadcastService.sendSubscriptionError(sessionId, "No symbols provided for subscription");
                return;
            }

            // Normalize and validate symbols
            Set<String> normalizedSymbols = new HashSet<>();
            for (String symbol : request.getSymbols()) {
                String upperSymbol = symbol.toUpperCase();
                if (simulationService.isSymbolAvailable(upperSymbol)) {
                    normalizedSymbols.add(upperSymbol);
                } else {
                    log.warn("Symbol {} is not available", upperSymbol);
                }
            }

            if (normalizedSymbols.isEmpty()) {
                broadcastService.sendSubscriptionError(sessionId, "None of the requested symbols are available");
                return;
            }

            // Handles the subscription action
            handleSubscriptionAction(request.getAction(), sessionId, userId, normalizedSymbols);

        } catch (Exception e) {
            log.error("Error processing subscription request from session: {}", sessionId, e);
            broadcastService.sendSubscriptionError(sessionId, "Internal error processing subscription");
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
        subscriptionService.updateActivity(sessionId);
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
        subscriptionService.updateActivity(sessionId);
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
                broadcastService.sendSubscriptionError(sessionId, "Unknown action: " + action);
        }
    }

    /**
     * Handles subscriptions to specific symbols
     */
    private void handleSubscribe(String sessionId, String userId, Set<String> symbols) {
        try {
            MarketSubscription subscription = subscriptionService.createOrUpdateSubscription(sessionId, userId,
                    symbols);
            broadcastService.sendSubscriptionSuccess(sessionId, symbols);

            log.info("Successfully subscribed session {} to symbols: {}", sessionId, symbols);

        } catch (Exception e) {
            log.error("Error creating subscription for session: {}", sessionId, e);
            broadcastService.sendSubscriptionError(sessionId, "Failed to create subscription");
        }
    }

    /**
     * Handles unsubscriptions from symbols
     */
    private void handleUnsubscribe(String sessionId, Set<String> symbols) {
        try {
            if (symbols.isEmpty()) {
                // Complete unsubscription
                subscriptionService.removeSubscription(sessionId);
                broadcastService.sendSubscriptionSuccess(sessionId, Set.of("all"));
                log.info("Unsubscribed session {} from all symbols", sessionId);
            } else {
                // Partial unsubscription
                subscriptionService.removeSymbolsFromSubscription(sessionId, symbols);
                broadcastService.sendSubscriptionSuccess(sessionId, symbols);
                log.info("Unsubscribed session {} from symbols: {}", sessionId, symbols);
            }
        } catch (Exception e) {
            log.error("Error unsubscribing session: {}", sessionId, e);
            broadcastService.sendSubscriptionError(sessionId, "Failed to unsubscribe");
        }
    }

    /**
     * Handles adding symbols to an existing subscription
     */
    private void handleAddSymbols(String sessionId, Set<String> symbols) {
        try {
            subscriptionService.addSymbolsToSubscription(sessionId, symbols);
            broadcastService.sendSubscriptionSuccess(sessionId, symbols);
            log.info("Added symbols {} to subscription {}", symbols, sessionId);
        } catch (Exception e) {
            log.error("Error adding symbols to subscription {}", sessionId, e);
            broadcastService.sendSubscriptionError(sessionId, "Failed to add symbols");
        }
    }

    /**
     * Handles removing symbols from an existing subscription
     */
    private void handleRemoveSymbols(String sessionId, Set<String> symbols) {
        try {
            subscriptionService.removeSymbolsFromSubscription(sessionId, symbols);
            broadcastService.sendSubscriptionSuccess(sessionId, symbols);
            log.info("Removed symbols {} from subscription {}", symbols, sessionId);
        } catch (Exception e) {
            log.error("Error removing symbols from subscription {}", sessionId, e);
            broadcastService.sendSubscriptionError(sessionId, "Failed to remove symbols");
        }
    }
}