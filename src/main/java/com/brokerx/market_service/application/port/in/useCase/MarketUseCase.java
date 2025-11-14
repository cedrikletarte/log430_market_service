package com.brokerx.market_service.application.port.in.useCase;

import java.util.Map;

import com.brokerx.market_service.domain.model.MarketData;

/**
 * Input port for market use case (hexagonal architecture)
 */
public interface MarketUseCase {

    void initialize();
    void updateAndBroadcast();
    MarketData getMarketDataById(Long id);
    MarketData getMarketData(String symbol);
    Map<String, MarketData> getAllMarketData();
    boolean isSymbolAvailable(String symbol);
    
}
