package com.brokerx.market_service.application.service;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for monitoring the health of market data and system status
 */
@Service
@RequiredArgsConstructor
public class MarketDataHealthService {

    private static final Logger logger = LogManager.getLogger(MarketDataHealthService.class);

    private final MarketDataBroadcastService broadcastService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Value("${market.health.stale-data-threshold:30}")
    private int staleDataThresholdSeconds;

    @Value("${market.health.degraded-mode-threshold:60}")
    private int degradedModeThresholdSeconds;

    @Value("${market.health.check-interval:10}")
    private int healthCheckIntervalSeconds;

    // Tracking de la santé des données par symbole
    private final Map<String, SymbolHealth> symbolHealthMap = new ConcurrentHashMap<>();

    // État général du système
    private volatile SystemStatus systemStatus = SystemStatus.NORMAL;
    private volatile LocalDateTime lastStatusChange = LocalDateTime.now();

    @PostConstruct
    public void initialize() {
        startHealthMonitoring();
        logger.info("Market data health monitoring started");
    }

    /**
     * Starts the health monitoring of the system
     */
    private void startHealthMonitoring() {
        scheduler.scheduleAtFixedRate(
                this::performHealthCheck,
                healthCheckIntervalSeconds,
                healthCheckIntervalSeconds,
                TimeUnit.SECONDS);
    }

    /**
     * Performs a global health check
     */
    public void performHealthCheck() {
        LocalDateTime now = LocalDateTime.now();

        // Checks the health of each symbol
        symbolHealthMap.forEach((symbol, health) -> {
            checkSymbolHealth(symbol, health, now);
        });

        // Evaluates the overall system status
        evaluateSystemStatus();

        logger.debug("Health check completed. System status: {}", systemStatus);
    }

    /**
     * Updates the health information of a symbol
     */
    public void updateSymbolHealth(String symbol) {
        SymbolHealth health = symbolHealthMap.computeIfAbsent(symbol, k -> new SymbolHealth());
        health.lastUpdate = LocalDateTime.now();
        health.updateCount++;

        // Réinitialise les erreurs si les données sont fraîches
        if (health.status != SymbolStatus.HEALTHY) {
            health.status = SymbolStatus.HEALTHY;
            health.errorCount = 0;
            logger.info("Symbol {} returned to healthy status", symbol);
        }
    }

    /**
     * Reports an error for a symbol
     */
    public void reportSymbolError(String symbol, String errorMessage) {
        SymbolHealth health = symbolHealthMap.computeIfAbsent(symbol, k -> new SymbolHealth());
        health.errorCount++;
        health.lastError = errorMessage;
        health.lastErrorTime = LocalDateTime.now();

        logger.warn("Error reported for symbol {}: {} (error count: {})", symbol, errorMessage, health.errorCount);

        // Triggers an immediate check
        checkSymbolHealth(symbol, health, LocalDateTime.now());
    }

    /**
     * Checks the health of a specific symbol
     */
    private void checkSymbolHealth(String symbol, SymbolHealth health, LocalDateTime now) {
        SymbolStatus previousStatus = health.status;

        // Calculates the age of the last update
        long secondsSinceLastUpdate = java.time.Duration.between(health.lastUpdate, now).getSeconds();

        // Determines the new status
        if (secondsSinceLastUpdate > degradedModeThresholdSeconds || health.errorCount > 5) {
            health.status = SymbolStatus.UNAVAILABLE;
        } else if (secondsSinceLastUpdate > staleDataThresholdSeconds || health.errorCount > 2) {
            health.status = SymbolStatus.STALE;
        } else {
            health.status = SymbolStatus.HEALTHY;
        }

        // Notifies if the status has changed
        if (previousStatus != health.status) {
            handleStatusChange(symbol, previousStatus, health.status);
        }
    }

    /**
     * Handles the status changes of a symbol
     */
    private void handleStatusChange(String symbol, SymbolStatus from, SymbolStatus to) {
        logger.info("Symbol {} status changed from {} to {}", symbol, from, to);

        switch (to) {
            case STALE:
                broadcastService.broadcastStaleDataAlert(symbol);
                break;
            case UNAVAILABLE:
                broadcastService.broadcastDegradedMode(symbol);
                break;
            case HEALTHY:
                // Returns to normal - no special notification needed
                break;
        }
    }

    /**
     * Evaluates the overall system status
     */
    private void evaluateSystemStatus() {
        if (symbolHealthMap.isEmpty()) {
            updateSystemStatus(SystemStatus.NORMAL);
            return;
        }

        long totalSymbols = symbolHealthMap.size();
        long unavailableSymbols = symbolHealthMap.values().stream()
                .mapToLong(h -> h.status == SymbolStatus.UNAVAILABLE ? 1 : 0)
                .sum();
        long staleSymbols = symbolHealthMap.values().stream()
                .mapToLong(h -> h.status == SymbolStatus.STALE ? 1 : 0)
                .sum();

        double unavailablePercentage = (double) unavailableSymbols / totalSymbols * 100;
        double degradedPercentage = (double) (unavailableSymbols + staleSymbols) / totalSymbols * 100;

        SystemStatus newStatus;
        if (unavailablePercentage > 50) {
            newStatus = SystemStatus.CRITICAL;
        } else if (degradedPercentage > 30) {
            newStatus = SystemStatus.DEGRADED;
        } else if (degradedPercentage > 10) {
            newStatus = SystemStatus.WARNING;
        } else {
            newStatus = SystemStatus.NORMAL;
        }

        updateSystemStatus(newStatus);
    }

    /**
     * Updates the system status
     */
    private void updateSystemStatus(SystemStatus newStatus) {
        if (systemStatus != newStatus) {
            SystemStatus previousStatus = systemStatus;
            systemStatus = newStatus;
            lastStatusChange = LocalDateTime.now();

            logger.warn("System status changed from {} to {}", previousStatus, newStatus);

            // Déclenche des actions selon le nouveau statut
            handleSystemStatusChange(newStatus);
        }
    }

    /**
     * Handles the status changes of the system
     */
    private void handleSystemStatusChange(SystemStatus status) {
        switch (status) {
            case CRITICAL:
                logger.error("CRITICAL: Market data system is in critical state");
                // Here we could trigger alerts, notifications, etc.
                break;
            case DEGRADED:
                logger.warn("WARNING: Market data system is in degraded mode");
                break;
            case WARNING:
                logger.warn("WARNING: Market data system quality is degraded");
                break;
            case NORMAL:
                logger.info("Market data system returned to normal operation");
                break;
        }
    }

    /**
     * Forces the degraded mode for a symbol
     */
    public void forceSymbolDegradedMode(String symbol, String reason) {
        SymbolHealth health = symbolHealthMap.computeIfAbsent(symbol, k -> new SymbolHealth());
        health.status = SymbolStatus.UNAVAILABLE;
        health.lastError = "Forced degraded mode: " + reason;
        health.lastErrorTime = LocalDateTime.now();

        logger.warn("Forced symbol {} into degraded mode: {}", symbol, reason);
        broadcastService.broadcastDegradedMode(symbol);
    }

    /**
     * Retrieves the health status of a symbol
     */
    public SymbolHealth getSymbolHealth(String symbol) {
        return symbolHealthMap.get(symbol);
    }

    /**
     * Retrieves the overall system status
     */
    public SystemStatus getSystemStatus() {
        return systemStatus;
    }

    /**
     * Retrieves the health statistics
     */
    public HealthStats getHealthStats() {
        long totalSymbols = symbolHealthMap.size();
        long healthySymbols = symbolHealthMap.values().stream()
                .mapToLong(h -> h.status == SymbolStatus.HEALTHY ? 1 : 0)
                .sum();
        long staleSymbols = symbolHealthMap.values().stream()
                .mapToLong(h -> h.status == SymbolStatus.STALE ? 1 : 0)
                .sum();
        long unavailableSymbols = symbolHealthMap.values().stream()
                .mapToLong(h -> h.status == SymbolStatus.UNAVAILABLE ? 1 : 0)
                .sum();

        return new HealthStats(systemStatus, totalSymbols, healthySymbols, staleSymbols, unavailableSymbols);
    }

    /**
     * Stops the monitoring service
     */
    public void shutdown() {
        scheduler.shutdown();
        logger.info("Market data health service shutdown");
    }

    /**
     * Class to track the health of a symbol
     */
    public static class SymbolHealth {
        public volatile SymbolStatus status = SymbolStatus.HEALTHY;
        public volatile LocalDateTime lastUpdate = LocalDateTime.now();
        public volatile int updateCount = 0;
        public volatile int errorCount = 0;
        public volatile String lastError;
        public volatile LocalDateTime lastErrorTime;
    }

    /**
     * Enumeration of symbol statuses
     */
    public enum SymbolStatus {
        HEALTHY, // Fresh and available data
        STALE, // Stale but available data
        UNAVAILABLE // Unavailable data
    }

    /**
     * Enumeration of system statuses
     */
    public enum SystemStatus {
        NORMAL, // Normal operation
        WARNING, // Some issues detected
        DEGRADED, // Degraded mode
        CRITICAL // Critical state
    }

    /**
     * Health statistics of the system
     */
    public record HealthStats(
            SystemStatus systemStatus,
            long totalSymbols,
            long healthySymbols,
            long staleSymbols,
            long unavailableSymbols) {
    }
}