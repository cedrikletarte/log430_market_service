package com.brokerx.market_service.application.service;

import com.brokerx.market_service.adapter.web.dto.MarketDataDto;
import com.brokerx.market_service.adapter.web.dto.SubscriptionResponseDto;
import com.brokerx.market_service.application.port.in.BroadcastMarketDataUseCase;
import com.brokerx.market_service.application.port.in.GetMarketDataUseCase;
import com.brokerx.market_service.application.port.in.ManageSubscriptionUseCase;
import com.brokerx.market_service.domain.model.MarketData;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service responsible for broadcasting market data to subscribed clients
 * Implements hexagonal architecture with use case ports
 */
@Service
@RequiredArgsConstructor
public class MarketDataBroadcastService implements BroadcastMarketDataUseCase {

    private static final Logger logger = LogManager.getLogger(MarketDataBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final GetMarketDataUseCase getMarketDataUseCase;
    private final ManageSubscriptionUseCase manageSubscriptionUseCase;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void initialize() {
        // Use a single synchronized broadcaster to ensure identical values/timestamps
        startSynchronizedBroadcasting();
        startCleanupTask();
    }

    /**
     * Starts periodic broadcasting for both individual symbols and bulk in the same tick
     * to guarantee identical values and timestamps for all recipients (5 second interval)
     */
    private void startSynchronizedBroadcasting() {
        scheduler.scheduleAtFixedRate(() -> {
            var allMarketData = getMarketDataUseCase.getAllMarketData();
            if (allMarketData.isEmpty()) {
                return;
            }

            // Use a single timestamp for this tick to guarantee synchronicity
            String timestamp = LocalDateTime.now().toString();

            // Convert all market data to DTOs once
            var marketDataDtos = allMarketData.entrySet().stream()
                .collect(Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    entry -> convertToDto(entry.getValue(), "live")
                ));

            // Broadcast per-symbol only to those with subscribers, with the same timestamp
            marketDataDtos.forEach((symbol, dto) -> {
                Set<String> subscribers = manageSubscriptionUseCase.getSubscribersForSymbol(symbol);
                if (!subscribers.isEmpty()) {
                    SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                            .type("market_data")
                            .data(dto)
                            .timestamp(timestamp)
                            .build();
                    messagingTemplate.convertAndSend("/topic/market/" + symbol, response);
                }
            });

            // Broadcast bulk snapshot with the exact same data and timestamp
            SubscriptionResponseDto bulkResponse = SubscriptionResponseDto.builder()
                    .type("bulk_market_data")
                    .data(marketDataDtos)
                    .timestamp(timestamp)
                    .message("Bulk market data update - " + marketDataDtos.size() + " symbols")
                    .build();
            messagingTemplate.convertAndSend("/topic/market/all", bulkResponse);

            logger.debug("Synchronized broadcast completed for {} symbols", marketDataDtos.size());
        }, 5000, 5000, TimeUnit.MILLISECONDS);
        logger.info("Synchronized market data broadcasting started (5s interval)");
    }

    /**
     * Starts the task of cleaning up expired subscriptions
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            manageSubscriptionUseCase.cleanupExpiredSubscriptions();
        }, 60, 60, TimeUnit.SECONDS);
        logger.info("Started subscription cleanup task");
    }

    @Override
    public void sendSubscriptionSuccess(String sessionId, Set<String> symbols) {
        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .type("subscription_success")
                .message("Successfully subscribed to symbols: " + symbols)
                .timestamp(LocalDateTime.now().toString())
                .build();

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/subscription", response);
        logger.info("Sent subscription success to session: {} for symbols: {}", sessionId, symbols);
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
        logger.warn("Sent subscription error to session: {} - {}", sessionId, errorMessage);
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
        logger.info("Market data broadcast service shutdown");
    }
}