package com.brokerx.market_service.application.service;

import com.brokerx.market_service.adapter.web.dto.MarketDataDto;
import com.brokerx.market_service.adapter.web.dto.SubscriptionResponseDto;
import com.brokerx.market_service.application.port.in.useCase.MarketUseCase;
import com.brokerx.market_service.domain.model.MarketData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/* MarketDataService handles market data simulation and broadcasting */
@Service
@RequiredArgsConstructor
public class MarketDataService implements MarketUseCase {

    private static final Logger logger = LogManager.getLogger(MarketDataService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    private final Map<String, MarketData> marketData = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Value("${market.simulation.volatility:0.02}")
    private double volatility;

    /* Initializes market data and starts periodic updates */
    @PostConstruct
    public void initialize() {
        loadInitialData();
        logger.info("Market data service initialized - simulating and broadcasting every 5 seconds");
    }

    /* Loads initial market data from market.json */
    private void loadInitialData() {
        try {
            ClassPathResource resource = new ClassPathResource("market.json");
            List<Map<String, Object>> initialData = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> data : initialData) {
                MarketData md = MarketData.builder()
                        .id(((Number) data.get("id")).longValue())
                        .symbol((String) data.get("symbol"))
                        .name((String) data.get("name"))
                        .lastPrice(new BigDecimal(data.get("lastPrice").toString()))
                        .bid(new BigDecimal(data.get("bid").toString()))
                        .ask(new BigDecimal(data.get("ask").toString()))
                        .volume(((Number) data.get("volume")).longValue())
                        .timestamp(LocalDateTime.now())
                        .build();

                marketData.put(md.getSymbol(), md);
            }

            logger.info("Loaded {} market symbols", marketData.size());
        } catch (IOException e) {
            logger.error("Failed to load initial market data", e);
        }
    }

    /**
     * Every 5 seconds: simulate price changes AND broadcast to subscribers
     * Spring WebSocket automatically sends only to subscribed clients
     */
    @Scheduled(fixedRate = 5000, initialDelay = 5000)
    public void updateAndBroadcast() {
        if (marketData.isEmpty()) {
            return;
        }

        // 1. Simulate price changes for all symbols
        marketData.values().forEach(this::simulatePriceChange);

        // 2. Broadcast immediately to subscribers
        String timestamp = LocalDateTime.now().toString();
        
        var marketDataDtos = marketData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> convertToDto(entry.getValue())
            ));

        // Broadcast to individual symbol topics
        marketDataDtos.forEach((symbol, dto) -> {
            SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                    .type("market_data")
                    .data(dto)
                    .timestamp(timestamp)
                    .build();
            messagingTemplate.convertAndSend("/topic/market/" + symbol, response);
        });

        // Broadcast bulk snapshot
        SubscriptionResponseDto bulkResponse = SubscriptionResponseDto.builder()
                .type("bulk_market_data")
                .data(marketDataDtos)
                .timestamp(timestamp)
                .message("Market data update - " + marketDataDtos.size() + " symbols")
                .build();
        messagingTemplate.convertAndSend("/topic/market/all", bulkResponse);

        logger.debug("Updated and broadcasted {} symbols", marketDataDtos.size());
    }

    /* Simulates realistic price changes for a symbol */
    private void simulatePriceChange(MarketData data) {
        double change = random.nextGaussian() * volatility;

        BigDecimal newLastPrice = data.getLastPrice()
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(change)))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal spread = newLastPrice.multiply(BigDecimal.valueOf(0.001)); // 0.1% spread
        BigDecimal newBid = newLastPrice.subtract(spread.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
        BigDecimal newAsk = newLastPrice.add(spread.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));

        long volumeChange = (long) (random.nextGaussian() * 1000);
        long newVolume = Math.max(0, data.getVolume() + volumeChange);

        data.setLastPrice(newLastPrice);
        data.setBid(newBid);
        data.setAsk(newAsk);
        data.setVolume(newVolume);
        data.setTimestamp(LocalDateTime.now());
    }

    private MarketDataDto convertToDto(MarketData data) {
        return MarketDataDto.builder()
                .symbol(data.getSymbol())
                .name(data.getName())
                .lastPrice(data.getLastPrice())
                .bid(data.getBid())
                .ask(data.getAsk())
                .spread(data.getSpread())
                .midPrice(data.getMidPrice())
                .volume(data.getVolume())
                .timestamp(data.getTimestamp())
                .status("live")
                .build();
    }

    /* Retrieves the current market data by ID */
    public MarketData getMarketDataById(Long id) {
        return marketData.values().stream()
                .filter(md -> md.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /* Retrieves the current market data by symbol */
    public MarketData getMarketData(String symbol) {
        return marketData.get(symbol.toUpperCase());
    }

    /* Retrieves all current market data as a map */
    public Map<String, MarketData> getAllMarketData() {
        return Map.copyOf(marketData);
    }

    /* Checks if a stock symbol is available */
    public boolean isSymbolAvailable(String symbol) {
        return marketData.containsKey(symbol.toUpperCase());
    }
}