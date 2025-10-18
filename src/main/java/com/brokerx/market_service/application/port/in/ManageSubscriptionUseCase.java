package com.brokerx.market_service.application.port.in;

import com.brokerx.market_service.domain.model.MarketSubscription;

import java.util.Set;

/**
 * Use case for managing market data subscriptions
 */
public interface ManageSubscriptionUseCase {
    
    /**
     * Retrieves a subscription by session ID
     */
    MarketSubscription getSubscription(String sessionId);
    
    /**
     * Retrieves all sessions subscribed to a given symbol
     */
    Set<String> getSubscribersForSymbol(String symbol);
    
    /**
     * Deletes a subscription completely
     */
    void removeSubscription(String sessionId);
    
    /**
     * Deactivates a subscription (without deleting it)
     */
    void deactivateSubscription(String sessionId);
    
    /**
     * Updates the last activity timestamp of a subscription
     */
    void updateActivity(String sessionId);
    
    /**
     * Retrieves the total number of active subscriptions
     */
    int getActiveSubscriptionsCount();
    
    /**
     * Cleans up expired subscriptions
     */
    void cleanupExpiredSubscriptions();
}
