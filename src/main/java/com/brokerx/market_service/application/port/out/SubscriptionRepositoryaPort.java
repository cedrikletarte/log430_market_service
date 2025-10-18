package com.brokerx.market_service.application.port.out;

import com.brokerx.market_service.domain.model.MarketSubscription;

import java.util.Optional;
import java.util.Set;

/**
 * Port for persisting and retrieving market subscriptions
 */
public interface SubscriptionRepositoryaPort {
    
    /**
     * Saves a subscription
     */
    MarketSubscription save(MarketSubscription subscription);
    
    /**
     * Finds a subscription by session ID
     */
    Optional<MarketSubscription> findBySessionId(String sessionId);
    
    /**
     * Deletes a subscription by session ID
     */
    void deleteBySessionId(String sessionId);
    
    /**
     * Finds all active subscriptions
     */
    Set<MarketSubscription> findAllActive();
    
    /**
     * Finds all subscriptions (active and inactive)
     */
    Set<MarketSubscription> findAll();
    
    /**
     * Counts active subscriptions
     */
    long countActive();
}
