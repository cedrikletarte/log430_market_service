package com.brokerx.market_service.application.service;

import com.brokerx.market_service.domain.model.MarketSubscription;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Service for managing market data subscriptions
 */
@Service
public class MarketSubscriptionService {

    private static final Logger logger = LogManager.getLogger(MarketSubscriptionService.class);

    private final Map<String, MarketSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> symbolSubscribers = new ConcurrentHashMap<>();

    /**
     * Creates or updates a subscription for a user
     */
    public MarketSubscription createOrUpdateSubscription(String sessionId, String userId, Set<String> symbols) {
        MarketSubscription subscription = subscriptions.get(sessionId);

        if (subscription == null) {
            subscription = MarketSubscription.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .subscribedSymbols(new HashSet<>(symbols))
                    .createdAt(LocalDateTime.now())
                    .lastActivity(LocalDateTime.now())
                    .isActive(true)
                    .build();

            subscriptions.put(sessionId, subscription);
            logger.info("Created new subscription for session: {} with symbols: {}", sessionId, symbols);
        } else {
            // Removes old symbol subscriptions
            removeSymbolSubscriptions(sessionId, subscription.getSubscribedSymbols());

            // Updates with new symbols
            subscription.setSubscribedSymbols(new HashSet<>(symbols));
            subscription.updateActivity();
            logger.info("Updated subscription for session: {} with symbols: {}", sessionId, symbols);
        }

        // Adds new symbol subscriptions
        addSymbolSubscriptions(sessionId, symbols);

        return subscription;
    }

    /**
     * Adds symbols to an existing subscription
     */
    public void addSymbolsToSubscription(String sessionId, Set<String> symbols) {
        MarketSubscription subscription = subscriptions.get(sessionId);
        if (subscription != null && subscription.isActive()) {
            subscription.getSubscribedSymbols().addAll(symbols);
            subscription.updateActivity();
            addSymbolSubscriptions(sessionId, symbols);
            logger.info("Added symbols {} to subscription {}", symbols, sessionId);
        }
    }

    /**
     * Removes symbols from an existing subscription
     */
    public void removeSymbolsFromSubscription(String sessionId, Set<String> symbols) {
        MarketSubscription subscription = subscriptions.get(sessionId);
        if (subscription != null && subscription.isActive()) {
            subscription.getSubscribedSymbols().removeAll(symbols);
            subscription.updateActivity();
            removeSymbolSubscriptions(sessionId, symbols);
            logger.info("Removed symbols {} from subscription {}", symbols, sessionId);
        }
    }

    /**
     * Deletes a subscription completely
     */
    public void removeSubscription(String sessionId) {
        MarketSubscription subscription = subscriptions.remove(sessionId);
        if (subscription != null) {
            removeSymbolSubscriptions(sessionId, subscription.getSubscribedSymbols());
            logger.info("Removed subscription for session: {}", sessionId);
        }
    }

    /**
     * Deactivates a subscription (without deleting it)
     */
    public void deactivateSubscription(String sessionId) {
        MarketSubscription subscription = subscriptions.get(sessionId);
        if (subscription != null) {
            subscription.setActive(false);
            removeSymbolSubscriptions(sessionId, subscription.getSubscribedSymbols());
            logger.info("Deactivated subscription for session: {}", sessionId);
        }
    }

    /**
     * Retrieves a subscription by session ID
     */
    public MarketSubscription getSubscription(String sessionId) {
        return subscriptions.get(sessionId);
    }

    /**
     * Retrieves all sessions subscribed to a given symbol
     */
    public Set<String> getSubscribersForSymbol(String symbol) {
        return symbolSubscribers.getOrDefault(symbol.toUpperCase(), new HashSet<>());
    }

    /**
     * Updates the last activity timestamp of a subscription
     */
    public void updateActivity(String sessionId) {
        MarketSubscription subscription = subscriptions.get(sessionId);
        if (subscription != null) {
            subscription.updateActivity();
        }
    }

    /**
     * Cleans up expired subscriptions
     */
    public void cleanupExpiredSubscriptions() {
        subscriptions.entrySet().removeIf(entry -> {
            MarketSubscription subscription = entry.getValue();
            if (!subscription.isValid()) {
                removeSymbolSubscriptions(entry.getKey(), subscription.getSubscribedSymbols());
                logger.info("Cleaned up expired subscription: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Adds subscriptions to symbols
     */
    private void addSymbolSubscriptions(String sessionId, Set<String> symbols) {
        symbols.forEach(symbol -> {
            String upperSymbol = symbol.toUpperCase();
            symbolSubscribers.computeIfAbsent(upperSymbol, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        });
    }

    /**
     * Removes subscriptions from symbols
     */
    private void removeSymbolSubscriptions(String sessionId, Set<String> symbols) {
        symbols.forEach(symbol -> {
            String upperSymbol = symbol.toUpperCase();
            Set<String> subscribers = symbolSubscribers.get(upperSymbol);
            if (subscribers != null) {
                subscribers.remove(sessionId);
                if (subscribers.isEmpty()) {
                    symbolSubscribers.remove(upperSymbol);
                }
            }
        });
    }

    /**
     * Retrieves the total number of active subscriptions
     */
    public int getActiveSubscriptionsCount() {
        return (int) subscriptions.values().stream()
                .filter(MarketSubscription::isValid)
                .count();
    }
}