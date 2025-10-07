package com.brokerx.market_service.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing user quotas and rate limiting
 *
 */
@Service
public class QuotaAndRateLimitService {

    private static final Logger logger = LogManager.getLogger(QuotaAndRateLimitService.class);

    @Value("${market.quota.max-symbols-per-user:10}")
    private int maxSymbolsPerUser;

    @Value("${market.quota.max-connections-per-user:5}")
    private int maxConnectionsPerUser;

    @Value("${market.ratelimit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${market.ratelimit.throttle-threshold:80}")
    private int throttleThreshold; // Percentage of quota before throttling

    // Tracking user quotas
    private final Map<String, UserQuota> userQuotas = new ConcurrentHashMap<>();

    // Tracking rate limiting by session
    private final Map<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();

    /**
     * Checks if a user can subscribe to additional symbols
     */
    public QuotaCheckResult checkSymbolQuota(String userId, int additionalSymbols) {
        UserQuota quota = userQuotas.computeIfAbsent(userId, k -> new UserQuota());

        int totalSymbols = quota.currentSymbols + additionalSymbols;

        if (totalSymbols > maxSymbolsPerUser) {
            logger.warn("Symbol quota exceeded for user {}: {} > {}", userId, totalSymbols, maxSymbolsPerUser);
            return QuotaCheckResult.failure("Symbol quota exceeded. Maximum allowed: " + maxSymbolsPerUser);
        }

        return QuotaCheckResult.success();
    }

    /**
     * Checks if a user can establish a new connection
     */
    public QuotaCheckResult checkConnectionQuota(String userId) {
        UserQuota quota = userQuotas.computeIfAbsent(userId, k -> new UserQuota());

        if (quota.currentConnections >= maxConnectionsPerUser) {
            logger.warn("Connection quota exceeded for user {}: {} >= {}", userId, quota.currentConnections,
                    maxConnectionsPerUser);
            return QuotaCheckResult.failure("Connection quota exceeded. Maximum allowed: " + maxConnectionsPerUser);
        }

        return QuotaCheckResult.success();
    }

    /**
     * Checks rate limiting for a session
     */
    public RateLimitResult checkRateLimit(String sessionId) {
        RateLimitTracker tracker = rateLimitTrackers.computeIfAbsent(sessionId, k -> new RateLimitTracker());

        LocalDateTime now = LocalDateTime.now();

        // Cleans up old counters (older than one minute)
        tracker.cleanOldRequests(now);

        // Checks if the limit is exceeded
        if (tracker.requestCount.get() >= requestsPerMinute) {
            logger.warn("Rate limit exceeded for session {}: {} requests per minute", sessionId,
                    tracker.requestCount.get());
            return RateLimitResult
                    .rateLimited("Rate limit exceeded. Maximum: " + requestsPerMinute + " requests per minute");
        }

        // Checks if throttling is necessary
        int currentRequests = tracker.requestCount.incrementAndGet();
        double usagePercentage = (double) currentRequests / requestsPerMinute * 100;

        if (usagePercentage >= throttleThreshold) {
            logger.info("Throttling session {}: {}% of rate limit used", sessionId, usagePercentage);
            return RateLimitResult.throttled("High request rate detected. Entering throttled mode.");
        }

        tracker.lastRequestTime = now;
        return RateLimitResult.allowed();
    }

    /**
     * Updates the symbol quotas upon a new subscription
     */
    public void updateSymbolQuota(String userId, int symbolCount) {
        UserQuota quota = userQuotas.computeIfAbsent(userId, k -> new UserQuota());
        quota.currentSymbols = symbolCount;
        logger.debug("Updated symbol quota for user {}: {} symbols", userId, symbolCount);
    }

    /**
     * Updates the connection quotas upon a new connection
     */
    public void incrementConnectionQuota(String userId) {
        UserQuota quota = userQuotas.computeIfAbsent(userId, k -> new UserQuota());
        quota.currentConnections++;
        logger.debug("Incremented connection quota for user {}: {} connections", userId, quota.currentConnections);
    }

    /**
     * Updates the connection quotas upon a disconnection
     */
    public void decrementConnectionQuota(String userId) {
        UserQuota quota = userQuotas.get(userId);
        if (quota != null && quota.currentConnections > 0) {
            quota.currentConnections--;
            logger.debug("Decremented connection quota for user {}: {} connections", userId, quota.currentConnections);
        }
    }

    /**
     * Deletes user quota data (cleanup)
     */
    public void removeUserData(String userId) {
        userQuotas.remove(userId);
        logger.debug("Removed quota data for user: {}", userId);
    }

    /**
     * Deletes session data (cleanup)
     */
    public void removeSessionData(String sessionId) {
        rateLimitTrackers.remove(sessionId);
        logger.debug("Removed rate limit data for session: {}", sessionId);
    }

    /**
     * Cleans up old rate limiting trackers
     */
    public void cleanupOldTrackers() {
        LocalDateTime cutoff = LocalDateTime.now().minus(5, ChronoUnit.MINUTES);

        rateLimitTrackers.entrySet().removeIf(entry -> {
            RateLimitTracker tracker = entry.getValue();
            if (tracker.lastRequestTime.isBefore(cutoff)) {
                logger.debug("Cleaned up old rate limit tracker for session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Internal class to track user quotas
     */
    private static class UserQuota {
        volatile int currentSymbols = 0;
        volatile int currentConnections = 0;
    }

    /**
     * Internal class to track rate limiting
     */
    private static class RateLimitTracker {
        final AtomicInteger requestCount = new AtomicInteger(0);
        volatile LocalDateTime lastRequestTime = LocalDateTime.now();
        volatile LocalDateTime windowStart = LocalDateTime.now();

        void cleanOldRequests(LocalDateTime now) {
            // Reset si plus d'une minute s'est écoulée
            if (ChronoUnit.MINUTES.between(windowStart, now) >= 1) {
                requestCount.set(0);
                windowStart = now;
            }
        }
    }

    /**
     * Result of quota verification
     */
    public static class QuotaCheckResult {
        public final boolean allowed;
        public final String message;

        private QuotaCheckResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public static QuotaCheckResult success() {
            return new QuotaCheckResult(true, null);
        }

        public static QuotaCheckResult failure(String message) {
            return new QuotaCheckResult(false, message);
        }
    }

    /**
     * RResult of rate limit verification
     */
    public static class RateLimitResult {
        public final boolean allowed;
        public final boolean throttled;
        public final String message;

        private RateLimitResult(boolean allowed, boolean throttled, String message) {
            this.allowed = allowed;
            this.throttled = throttled;
            this.message = message;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, false, null);
        }

        public static RateLimitResult throttled(String message) {
            return new RateLimitResult(true, true, message);
        }

        public static RateLimitResult rateLimited(String message) {
            return new RateLimitResult(false, false, message);
        }
    }
}