package com.brokerx.market_service.application.service;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Service responsible for simulating market data
 */
@Service
public class MarketDataSimulationService {

    private static final Logger logger = LogManager.getLogger(MarketDataSimulationService.class);

    private final ObjectMapper objectMapper;
    private final Map<String, MarketData> currentMarketData = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Value("${market.simulation.interval:1000}")
    private long simulationInterval;

    @Value("${market.simulation.volatility:0.02}")
    private double volatility;

    public MarketDataSimulationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        loadInitialData();
        startSimulation();
    }

    /**
     * Loads initial data from the market.json file
     */
    private void loadInitialData() {
        try {
            ClassPathResource resource = new ClassPathResource("market.json");
            List<Map<String, Object>> initialData = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            for (Map<String, Object> data : initialData) {
                MarketData marketData = MarketData.builder()
                        .id(((Number) data.get("id")).longValue())
                        .symbol((String) data.get("symbol"))
                        .name((String) data.get("name"))
                        .lastPrice(new BigDecimal(data.get("lastPrice").toString()))
                        .bid(new BigDecimal(data.get("bid").toString()))
                        .ask(new BigDecimal(data.get("ask").toString()))
                        .volume(((Number) data.get("volume")).longValue())
                        .timestamp(LocalDateTime.now())
                        .build();

                currentMarketData.put(marketData.getSymbol(), marketData);
            }

            logger.info("Loaded {} initial market data entries", currentMarketData.size());
        } catch (IOException e) {
            logger.error("Failed to load initial market data", e);
        }
    }

    /**
     * Starts the simulation of price variations
     */
    private void startSimulation() {
        scheduler.scheduleAtFixedRate(this::simulateMarketMovement, 0, simulationInterval, TimeUnit.MILLISECONDS);
        logger.info("Market data simulation started with interval: {}ms", simulationInterval);
    }

    /**
     * Simulates market movements for all symbols
     */
    private void simulateMarketMovement() {
        currentMarketData.values().forEach(this::updateMarketData);
    }

    /**
     * Updates market data for a given symbol with simulated changes
     */
    private void updateMarketData(MarketData marketData) {
        // Generates a random price movement based on volatility
        double change = (random.nextGaussian() * volatility);

        // Updates the last price
        BigDecimal newLastPrice = marketData.getLastPrice()
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(change)))
                .setScale(2, RoundingMode.HALF_UP);

        // Calculates the new bid and ask with a realistic spread
        BigDecimal spread = newLastPrice.multiply(BigDecimal.valueOf(0.001)); // 0.1% spread
        BigDecimal newBid = newLastPrice.subtract(spread.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
        BigDecimal newAsk = newLastPrice.add(spread.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));

        // Simulates trading volume
        long volumeChange = (long) (random.nextGaussian() * 1000);
        long newVolume = Math.max(0, marketData.getVolume() + volumeChange);

        // Updates the data
        marketData.setLastPrice(newLastPrice);
        marketData.setBid(newBid);
        marketData.setAsk(newAsk);
        marketData.setVolume(newVolume);
        marketData.setTimestamp(LocalDateTime.now());

        logger.debug("Updated market data for {}: price={}, bid={}, ask={}, volume={}",
                marketData.getSymbol(), newLastPrice, newBid, newAsk, newVolume);
    }

    /**
     * Retrieves the current market data for a symbol
     */
    public MarketData getMarketData(String symbol) {
        return currentMarketData.get(symbol.toUpperCase());
    }

    /**
     * Retrieves the current market data by ID
     */
    public MarketData getMarketDataById(Long id) {
        return currentMarketData.values().stream()
                .filter(md -> md.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves all current market data
     */
    public Map<String, MarketData> getAllMarketData() {
        return Map.copyOf(currentMarketData);
    }

    /**
     * Checks if a symbol is available
     */
    public boolean isSymbolAvailable(String symbol) {
        return currentMarketData.containsKey(symbol.toUpperCase());
    }

    /**
     * Stops the simulation
     */
    public void stopSimulation() {
        scheduler.shutdown();
        logger.info("Market data simulation stopped");
    }
}