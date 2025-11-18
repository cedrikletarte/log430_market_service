package com.brokerx.market_service.application.port.in.useCase;

import java.util.Map;

import com.brokerx.market_service.domain.model.MarketData;

public interface MarketUseCase {

    /* Initializes market data and starts periodic updates */
    void initialize();

    /* Updates market data and broadcasts to subscribers */
    void updateAndBroadcast();

    /* Retrieves market data by its unique ID */
    MarketData getMarketDataById(Long id);

    /* Retrieves market data by its symbol */
    MarketData getMarketData(String symbol);

    /* Retrieves all market data as a map of symbol to MarketData */
    Map<String, MarketData> getAllMarketData();

    /* Checks if a stock symbol is available */
    boolean isSymbolAvailable(String symbol);
}
