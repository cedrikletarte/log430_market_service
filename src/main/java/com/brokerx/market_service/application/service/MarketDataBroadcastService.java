package com.brokerx.market_service.application.service;

import com.brokerx.market_service.adapter.web.dto.MarketDataDto;
import com.brokerx.market_service.adapter.web.dto.SubscriptionResponseDto;
import com.brokerx.market_service.domain.model.MarketData;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service responsible for broadcasting market data to subscribed clients
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataSimulationService simulationService;
    private final MarketSubscriptionService subscriptionService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void initialize() {
        startBroadcasting();
        startBulkBroadcasting();
        startCleanupTask();
    }

    /**
     * Starts periodic broadcasting of individual market data (1 second interval)
     */
    private void startBroadcasting() {
        scheduler.scheduleAtFixedRate(this::broadcastIndividualMarketData, 1000, 1000, TimeUnit.MILLISECONDS);
        log.info("Individual market data broadcasting started (1s interval)");
    }

    /**
     * Starts periodic broadcasting of bulk market data for /topic/market/all (5 second interval)
     */
    private void startBulkBroadcasting() {
        scheduler.scheduleAtFixedRate(this::broadcastBulkMarketData, 5000, 5000, TimeUnit.MILLISECONDS);
        log.info("Bulk market data broadcasting started (5s interval)");
    }

    /**
     * Starts the task of cleaning up expired subscriptions
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(subscriptionService::cleanupExpiredSubscriptions, 60000, 60000,
                TimeUnit.MILLISECONDS);
        log.info("Subscription cleanup task started");
    }

    /**
     * Broadcasts individual market data for specific symbol subscribers (1s interval)
     */
    @Async
    public void broadcastIndividualMarketData() {
        // Broadcast to specific subscribers of each symbol only
        simulationService.getAllMarketData().forEach((symbol, marketData) -> {
            Set<String> subscribers = subscriptionService.getSubscribersForSymbol(symbol);
            if (!subscribers.isEmpty()) {
                broadcastToSymbolSubscribers(symbol, marketData);
            }
        });
    }

    /**
     * Broadcasts ALL market data in one JSON to /topic/market/all subscribers (5s interval)
     */
    @Async
    public void broadcastBulkMarketData() {
        var allMarketData = simulationService.getAllMarketData();
        
        if (allMarketData.isEmpty()) {
            return;
        }

        // Convert all market data to DTOs
        var marketDataDtos = allMarketData.entrySet().stream()
            .collect(Collectors.toMap(
                java.util.Map.Entry::getKey,
                entry -> convertToDto(entry.getValue(), "live")
            ));

        // Create bulk response with all market data
        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("bulk_market_data")
                .data(marketDataDtos) // Use existing data field for bulk data
                .timestamp(LocalDateTime.now().toString())
                .message("Bulk market data update - " + marketDataDtos.size() + " symbols")
                .build();

        // Broadcast to all subscribers of /topic/market/all
        messagingTemplate.convertAndSend("/topic/market/all", response);
        
        log.debug("Broadcasted bulk market data ({} symbols) to /topic/market/all subscribers", marketDataDtos.size());
    }

    /**
     * Broadcasts data for a specific symbol to its subscribers
     */
    private void broadcastToSymbolSubscribers(String symbol, MarketData marketData) {
        MarketDataDto marketDataDto = convertToDto(marketData, "live");

        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("market_data")
                .data(marketDataDto)
                .timestamp(LocalDateTime.now().toString())
                .build();

        // Broadcast to all subscribers of the symbol
        messagingTemplate.convertAndSend("/topic/market/" + symbol, response);

        log.debug("Broadcasted market data for {} to {} subscribers", symbol,
                subscriptionService.getSubscribersForSymbol(symbol).size());
    }

    /**
     * Sends a successful subscription response
     */
    public void sendSubscriptionSuccess(String sessionId, Set<String> symbols) {
        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("subscription_success")
                .message("Successfully subscribed to symbols: " + symbols)
                .timestamp(LocalDateTime.now().toString())
                .build();

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/subscription", response);
        log.info("Sent subscription success to session: {} for symbols: {}", sessionId, symbols);
    }

    /**
     * Sends a subscription error response
     */
    public void sendSubscriptionError(String sessionId, String errorMessage) {
        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("subscription_error")
                .message(errorMessage)
                .timestamp(LocalDateTime.now().toString())
                .build();

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/subscription", response);
        log.warn("Sent subscription error to session: {} - {}", sessionId, errorMessage);
    }

    /**
     * Sends a general error message
     */
    public void sendError(String sessionId, String errorMessage) {
        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("error")
                .message(errorMessage)
                .timestamp(LocalDateTime.now().toString())
                .build();

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", response);
        log.error("Sent error to session: {} - {}", sessionId, errorMessage);
    }

    /**
     * Broadcasts a degraded mode data message
     */
    public void broadcastDegradedMode(String symbol) {
        MarketData marketData = simulationService.getMarketData(symbol);
        if (marketData != null) {
            MarketDataDto degradedData = convertToDto(marketData, "delayed");

            SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                    .type("market_data")
                    .data(degradedData)
                    .message("Data is in degraded mode - less frequent updates")
                    .timestamp(LocalDateTime.now().toString())
                    .build();

            messagingTemplate.convertAndSend("/topic/market/" + symbol, response);
            log.warn("Broadcasted degraded mode data for symbol: {}", symbol);
        }
    }

    /**
     * Broadcasts a stale data alert
     */
    public void broadcastStaleDataAlert(String symbol) {
        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("stale_data_alert")
                .message("Market data for " + symbol + " may be delayed or stale")
                .timestamp(LocalDateTime.now().toString())
                .build();

        messagingTemplate.convertAndSend("/topic/market/" + symbol, response);
        log.warn("Broadcasted stale data alert for symbol: {}", symbol);
    }

    /**
     * Converts MarketData to MarketDataDto
     */
    private MarketDataDto convertToDto(MarketData marketData, String status) {
        return MarketDataDto.builder()
                .symbol(marketData.getSymbol())
                .name(marketData.getName())
                .lastPrice(marketData.getLastPrice())
                .bid(marketData.getBid())
                .ask(marketData.getAsk())
                .spread(marketData.getSpread())
                .midPrice(marketData.getMidPrice())
                .volume(marketData.getVolume())
                .timestamp(marketData.getTimestamp())
                .status(status)
                .build();
    }

    /**
     * Shuts down the broadcast service
     */
    public void shutdown() {
        scheduler.shutdown();
        log.info("Market data broadcast service shutdown");
    }
}