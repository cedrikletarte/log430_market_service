package com.brokerx.market_service.adapter.web.api;

import com.brokerx.market_service.application.service.MarketDataSimulationService;
import com.brokerx.market_service.domain.model.MarketData;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Rest Controller for accessing market data and system health information
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataRestController {

    private final MarketDataSimulationService simulationService;

    /**
     * Fetches all current market data
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, MarketData>> getAllMarketData() {
        Map<String, MarketData> marketData = simulationService.getAllMarketData();
        return ResponseEntity.ok(marketData);
    }

    /**
     * Fetches market data for a specific symbol
     */
    @GetMapping("/data/{symbol}")
    public ResponseEntity<MarketData> getMarketData(@PathVariable String symbol) {
        MarketData marketData = simulationService.getMarketData(symbol.toUpperCase());

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
        Map<String, MarketData> allData = simulationService.getAllMarketData();
        return ResponseEntity.ok(Map.of(
                "symbols", allData.keySet(),
                "count", allData.size()));
    }
}