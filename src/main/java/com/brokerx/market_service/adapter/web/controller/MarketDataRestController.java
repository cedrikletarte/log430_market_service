package com.brokerx.market_service.adapter.web.controller;

import com.brokerx.market_service.application.service.MarketDataHealthService;
import com.brokerx.market_service.application.service.MarketDataSimulationService;
import com.brokerx.market_service.application.service.MarketSubscriptionService;
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
@CrossOrigin(origins = "*") // Pour permettre les requêtes depuis le frontend
public class MarketDataRestController {

    private final MarketDataSimulationService simulationService;
    private final MarketDataHealthService healthService;
    private final MarketSubscriptionService subscriptionService;

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
     * Checks if a symbol is available
     */
    @GetMapping("/symbols/{symbol}/available")
    public ResponseEntity<Map<String, Object>> checkSymbolAvailability(@PathVariable String symbol) {
        boolean available = simulationService.isSymbolAvailable(symbol.toUpperCase());
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "available", available));
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

    /**
     * Fetches the system health status
     */
    @GetMapping("/health")
    public ResponseEntity<MarketDataHealthService.HealthStats> getSystemHealth() {
        MarketDataHealthService.HealthStats stats = healthService.getHealthStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Fetches the health status of a specific symbol
     */
    @GetMapping("/health/{symbol}")
    public ResponseEntity<MarketDataHealthService.SymbolHealth> getSymbolHealth(@PathVariable String symbol) {
        MarketDataHealthService.SymbolHealth health = healthService.getSymbolHealth(symbol.toUpperCase());

        if (health == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(health);
    }

    /**
     * Fetches the subscription statistics
     */
    @GetMapping("/subscriptions/stats")
    public ResponseEntity<Map<String, Object>> getSubscriptionStats() {
        int activeSubscriptions = subscriptionService.getActiveSubscriptionsCount();

        return ResponseEntity.ok(Map.of(
                "activeSubscriptions", activeSubscriptions,
                "timestamp", java.time.LocalDateTime.now()));
    }

    /**
     * Endpoint to force a symbol into degraded mode (for testing purposes)
     */
    @PostMapping("/health/{symbol}/degrade")
    public ResponseEntity<Map<String, String>> forceDegradedMode(
            @PathVariable String symbol,
            @RequestBody(required = false) Map<String, String> request) {

        String reason = request != null ? request.getOrDefault("reason", "Manual degradation") : "Manual degradation";
        healthService.forceSymbolDegradedMode(symbol.toUpperCase(), reason);

        return ResponseEntity.ok(Map.of(
                "message", "Symbol " + symbol.toUpperCase() + " forced into degraded mode",
                "reason", reason));
    }

    /**
     * Endpoint to check if the service is active
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "alive",
                "service", "market-data-service",
                "timestamp", java.time.LocalDateTime.now(),
                "systemStatus", healthService.getSystemStatus()));
    }
}