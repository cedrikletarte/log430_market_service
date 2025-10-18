package com.brokerx.market_service.application.port.out;

import java.util.Set;

/**
 * Port for managing symbol-to-subscriber mappings
 */
public interface SymbolSubscriberRepositoryPort {
    
    /**
     * Adds a subscriber to a symbol
     */
    void addSubscriber(String symbol, String sessionId);
    
    /**
     * Removes a subscriber from a symbol
     */
    void removeSubscriber(String symbol, String sessionId);
    
    /**
     * Retrieves all subscribers for a given symbol
     */
    Set<String> getSubscribers(String symbol);
    
    /**
     * Adds a subscriber to multiple symbols
     */
    void addSubscriberToSymbols(Set<String> symbols, String sessionId);
    
    /**
     * Removes a subscriber from multiple symbols
     */
    void removeSubscriberFromSymbols(Set<String> symbols, String sessionId);
}
