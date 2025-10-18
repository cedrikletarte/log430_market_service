package com.brokerx.market_service.application.port.in;

import com.brokerx.market_service.domain.model.MarketData;

import java.util.Map;

/**
 * Use case for retrieving market data
 */
public interface GetMarketDataUseCase {
    
    /**
     * Retrieves market data for a specific symbol
     * 
     * @param symbol the market symbol
     * @return the market data for the symbol, or null if not found
     */
    MarketData getMarketData(String symbol);
    
    /**
     * Retrieves all current market data
     * 
     * @return a map of all market data indexed by symbol
     */
    Map<String, MarketData> getAllMarketData();
    
    /**
     * Checks if a symbol is available
     * 
     * @param symbol the market symbol to check
     * @return true if the symbol is available, false otherwise
     */
    boolean isSymbolAvailable(String symbol);
}
