package com.brokerx.market_service.application.port.in;

import com.brokerx.market_service.domain.model.MarketSubscription;

import java.util.Set;

/**
 * Use case for subscribing to market data
 */
public interface SubscribeToMarketDataUseCase {
    
    /**
     * Creates or updates a subscription for a user
     */
    MarketSubscription subscribe(String sessionId, String userId, Set<String> symbols);
    
    /**
     * Adds symbols to an existing subscription
     */
    void addSymbols(String sessionId, Set<String> symbols);
    
    /**
     * Removes symbols from an existing subscription
     */
    void removeSymbols(String sessionId, Set<String> symbols);
}
