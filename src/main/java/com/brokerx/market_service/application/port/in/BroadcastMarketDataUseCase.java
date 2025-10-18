package com.brokerx.market_service.application.port.in;

import java.util.Set;

/**
 * Use case for broadcasting market data and subscription responses
 */
public interface BroadcastMarketDataUseCase {
    
    /**
     * Sends a subscription success message to a specific session
     */
    void sendSubscriptionSuccess(String sessionId, Set<String> symbols);
    
    /**
     * Sends a subscription error message to a specific session
     */
    void sendSubscriptionError(String sessionId, String errorMessage);
}
