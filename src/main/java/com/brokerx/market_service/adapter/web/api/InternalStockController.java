package com.brokerx.market_service.adapter.web.api;

import com.brokerx.market_service.application.service.MarketDataSimulationService;
import com.brokerx.market_service.domain.model.MarketData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.brokerx.market_service.adapter.web.dto.StockResponse;

/**
 * Controller for internal communication between microservices.
 * These endpoints are secured by the ServiceAuthenticationFilter
 * and should NOT be publicly exposed via the Gateway.
 * 
 */
@Slf4j
@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final MarketDataSimulationService marketDataSimulationService;

    /**
     * Validate that a stock symbol exists and return its information.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<StockResponse> validateStock(@PathVariable String symbol) {
        log.debug("Internal request: Validating stock symbol: {}", symbol);
        
        MarketData marketData = marketDataSimulationService.getMarketData(symbol);
        
        if (marketData == null) {
            log.warn("Stock symbol not found: {}", symbol);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(new StockResponse(
            marketData.getId(),
            marketData.getSymbol(),
            marketData.getName(),
            marketData.getLastPrice()
        ));
    }

    /**
     * Retrieved by other microservices to get stock information.
     */
    @GetMapping("/id/{stockId}")
    public ResponseEntity<StockResponse> getStockById(@PathVariable Long stockId) {
        log.debug("Internal request: Getting stock by id: {}", stockId);
        
        MarketData marketData = marketDataSimulationService.getMarketDataById(stockId);
        
        if (marketData == null) {
            log.warn("Stock id not found: {}", stockId);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(new StockResponse(
            marketData.getId(),
            marketData.getSymbol(),
            marketData.getName(),
            marketData.getLastPrice()
        ));
    }
}
