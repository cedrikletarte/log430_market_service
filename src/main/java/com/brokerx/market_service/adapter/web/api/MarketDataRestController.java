package com.brokerx.market_service.adapter.web.api;

import com.brokerx.market_service.application.port.in.GetMarketDataUseCase;
import com.brokerx.market_service.domain.model.MarketData;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for accessing market data and system health information
 * Uses hexagonal architecture - depends on use case ports
 */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataRestController {

    private final GetMarketDataUseCase getMarketDataUseCase;

    /**
     * Fetches all current market data
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, MarketData>> getAllMarketData() {
        Map<String, MarketData> marketData = getMarketDataUseCase.getAllMarketData();
        return ResponseEntity.ok(marketData);
    }

    /**
     * Fetches market data for a specific symbol
     */
    @GetMapping("/data/{symbol}")
    public ResponseEntity<MarketData> getMarketData(@PathVariable String symbol) {
        MarketData marketData = getMarketDataUseCase.getMarketData(symbol.toUpperCase());

        if (marketData == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(marketData);
    }

    /**
     * Fetches the list of all available symbols
     */
    @GetMapping("/symbols")
    public ResponseEntity<Map<String, Object>> getAvailableSymbols() {
        Map<String, MarketData> allData = getMarketDataUseCase.getAllMarketData();
        return ResponseEntity.ok(Map.of(
                "symbols", allData.keySet(),
                "count", allData.size()));
    }
}