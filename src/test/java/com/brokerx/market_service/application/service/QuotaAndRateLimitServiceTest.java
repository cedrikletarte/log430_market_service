package com.brokerx.market_service.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.brokerx.market_service.application.service.QuotaAndRateLimitService.QuotaCheckResult;
import com.brokerx.market_service.application.service.QuotaAndRateLimitService.RateLimitResult;

class QuotaAndRateLimitServiceTest {

    private QuotaAndRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new QuotaAndRateLimitService();
        
        // Set test values using reflection (simulating @Value injection)
        ReflectionTestUtils.setField(service, "maxSymbolsPerUser", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerUser", 5);
        ReflectionTestUtils.setField(service, "requestsPerMinute", 60);
        ReflectionTestUtils.setField(service, "throttleThreshold", 80);
    }

    // ===== Tests pour Symbol Quota =====

    @Test
    void shouldAllowSymbolSubscriptionWithinQuota() {
        QuotaCheckResult result = service.checkSymbolQuota("user-123", 5);

        assertTrue(result.allowed);
    }

    @Test
    void shouldAllowSymbolSubscriptionAtQuotaLimit() {
        QuotaCheckResult result = service.checkSymbolQuota("user-123", 10);

        assertTrue(result.allowed);
    }

    @Test
    void shouldRejectSymbolSubscriptionExceedingQuota() {
        QuotaCheckResult result = service.checkSymbolQuota("user-123", 11);

        assertFalse(result.allowed);
        assertNotNull(result.message);
        assertTrue(result.message.contains("Symbol quota exceeded"));
    }

    @Test
    void shouldTrackSymbolQuotaAcrossMultipleChecks() {
        service.updateSymbolQuota("user-123", 5);
        
        QuotaCheckResult result1 = service.checkSymbolQuota("user-123", 3); // 5 + 3 = 8
        assertTrue(result1.allowed);
        
        QuotaCheckResult result2 = service.checkSymbolQuota("user-123", 5); // 5 + 5 = 10
        assertTrue(result2.allowed);
        
        QuotaCheckResult result3 = service.checkSymbolQuota("user-123", 6); // 5 + 6 = 11
        assertFalse(result3.allowed);
    }

    @Test
    void shouldUpdateSymbolQuotaCorrectly() {
        service.updateSymbolQuota("user-123", 3);
        QuotaCheckResult result1 = service.checkSymbolQuota("user-123", 7); // 3 + 7 = 10
        assertTrue(result1.allowed);
        
        service.updateSymbolQuota("user-123", 8);
        QuotaCheckResult result2 = service.checkSymbolQuota("user-123", 2); // 8 + 2 = 10
        assertTrue(result2.allowed);
        
        QuotaCheckResult result3 = service.checkSymbolQuota("user-123", 3); // 8 + 3 = 11
        assertFalse(result3.allowed);
    }

    @Test
    void shouldHandleMultipleUsersIndependently() {
        service.updateSymbolQuota("user-1", 8);
        service.updateSymbolQuota("user-2", 3);
        
        QuotaCheckResult result1 = service.checkSymbolQuota("user-1", 2); // 8 + 2 = 10
        assertTrue(result1.allowed);
        
        QuotaCheckResult result2 = service.checkSymbolQuota("user-2", 7); // 3 + 7 = 10
        assertTrue(result2.allowed);
        
        QuotaCheckResult result3 = service.checkSymbolQuota("user-1", 3); // 8 + 3 = 11
        assertFalse(result3.allowed);
    }

    // ===== Tests pour Connection Quota =====

    @Test
    void shouldAllowFirstConnection() {
        QuotaCheckResult result = service.checkConnectionQuota("user-123");

        assertTrue(result.allowed);
    }

    @Test
    void shouldAllowConnectionsUpToLimit() {
        for (int i = 0; i < 5; i++) {
            service.incrementConnectionQuota("user-123");
        }
        
        QuotaCheckResult result = service.checkConnectionQuota("user-123");
        assertFalse(result.allowed);
        assertTrue(result.message.contains("Connection quota exceeded"));
    }

    @Test
    void shouldRejectConnectionExceedingQuota() {
        for (int i = 0; i < 5; i++) {
            service.incrementConnectionQuota("user-123");
        }
        
        QuotaCheckResult result = service.checkConnectionQuota("user-123");
        assertFalse(result.allowed);
    }

    @Test
    void shouldIncrementConnectionQuotaCorrectly() {
        service.incrementConnectionQuota("user-123");
        service.incrementConnectionQuota("user-123");
        service.incrementConnectionQuota("user-123");
        
        // 3 connections, can still add 2 more
        QuotaCheckResult result = service.checkConnectionQuota("user-123");
        assertTrue(result.allowed);
    }

    @Test
    void shouldDecrementConnectionQuotaCorrectly() {
        for (int i = 0; i < 5; i++) {
            service.incrementConnectionQuota("user-123");
        }
        
        // At limit
        QuotaCheckResult result1 = service.checkConnectionQuota("user-123");
        assertFalse(result1.allowed);
        
        // Decrement one connection
        service.decrementConnectionQuota("user-123");
        
        // Should now allow new connection
        QuotaCheckResult result2 = service.checkConnectionQuota("user-123");
        assertTrue(result2.allowed);
    }

    @Test
    void shouldHandleMultipleConnectionsAndDisconnections() {
        service.incrementConnectionQuota("user-123");
        service.incrementConnectionQuota("user-123");
        service.incrementConnectionQuota("user-123"); // 3 connections
        
        service.decrementConnectionQuota("user-123"); // 2 connections
        service.incrementConnectionQuota("user-123"); // 3 connections
        service.incrementConnectionQuota("user-123"); // 4 connections
        
        QuotaCheckResult result = service.checkConnectionQuota("user-123");
        assertTrue(result.allowed); // Can add one more (4 < 5)
    }

    @Test
    void shouldNotDecrementBelowZero() {
        service.decrementConnectionQuota("user-123"); // No connections to decrement
        service.decrementConnectionQuota("user-123"); // Still safe
        
        QuotaCheckResult result = service.checkConnectionQuota("user-123");
        assertTrue(result.allowed);
    }

    @Test
    void shouldHandleMultipleUsersConnectionsIndependently() {
        service.incrementConnectionQuota("user-1");
        service.incrementConnectionQuota("user-1");
        service.incrementConnectionQuota("user-2");
        
        QuotaCheckResult result1 = service.checkConnectionQuota("user-1");
        assertTrue(result1.allowed); // user-1 has 2 connections
        
        QuotaCheckResult result2 = service.checkConnectionQuota("user-2");
        assertTrue(result2.allowed); // user-2 has 1 connection
    }

    // ===== Tests pour Rate Limiting =====

    @Test
    void shouldAllowFirstRequest() {
        RateLimitResult result = service.checkRateLimit("session-123");

        assertTrue(result.allowed);
        assertFalse(result.throttled);
    }

    @Test
    void shouldAllowRequestsWithinLimit() {
        for (int i = 0; i < 40; i++) {
            RateLimitResult result = service.checkRateLimit("session-123");
            assertTrue(result.allowed);
        }
    }

    @Test
    void shouldThrottleAtThresholdPercentage() {
        // 80% of 60 = 48 requests
        for (int i = 0; i < 47; i++) {
            service.checkRateLimit("session-123");
        }
        
        RateLimitResult result = service.checkRateLimit("session-123"); // 48th request
        
        assertTrue(result.allowed);
        assertTrue(result.throttled);
        assertTrue(result.message.contains("throttled"));
    }

    @Test
    void shouldRateLimitAtMaxRequests() {
        // Make 60 requests (the limit)
        for (int i = 0; i < 60; i++) {
            service.checkRateLimit("session-123");
        }
        
        // 61st request should be rate limited
        RateLimitResult result = service.checkRateLimit("session-123");
        
        assertFalse(result.allowed);
        assertFalse(result.throttled);
        assertTrue(result.message.contains("Rate limit exceeded"));
    }

    @Test
    void shouldHandleMultipleSessionsIndependently() {
        // Session 1 makes many requests
        for (int i = 0; i < 50; i++) {
            service.checkRateLimit("session-1");
        }
        
        // Session 2's first request should be allowed
        RateLimitResult result = service.checkRateLimit("session-2");
        
        assertTrue(result.allowed);
        assertFalse(result.throttled);
    }

    @Test
    void shouldNotThrottleBeforeThreshold() {
        // 79% of 60 = 47.4, so 47 requests should not trigger throttling
        for (int i = 0; i < 47; i++) {
            RateLimitResult result = service.checkRateLimit("session-123");
            assertTrue(result.allowed);
            assertFalse(result.throttled);
        }
    }

    // ===== Tests pour Data Cleanup =====

    @Test
    void shouldRemoveUserData() {
        service.updateSymbolQuota("user-123", 5);
        service.incrementConnectionQuota("user-123");
        
        service.removeUserData("user-123");
        
        // After removal, should start fresh
        QuotaCheckResult symbolResult = service.checkSymbolQuota("user-123", 10);
        assertTrue(symbolResult.allowed);
        
        QuotaCheckResult connectionResult = service.checkConnectionQuota("user-123");
        assertTrue(connectionResult.allowed);
    }

    @Test
    void shouldRemoveSessionData() {
        // Make some requests
        for (int i = 0; i < 50; i++) {
            service.checkRateLimit("session-123");
        }
        
        service.removeSessionData("session-123");
        
        // After removal, should start fresh
        RateLimitResult result = service.checkRateLimit("session-123");
        assertTrue(result.allowed);
        assertFalse(result.throttled);
    }

    @Test
    void shouldHandleRemovalOfNonExistentUser() {
        // Should not throw exception
        service.removeUserData("non-existent-user");
        service.removeSessionData("non-existent-session");
    }

    // ===== Tests pour QuotaCheckResult =====

    @Test
    void shouldCreateSuccessQuotaCheckResult() {
        QuotaCheckResult result = QuotaCheckResult.success();

        assertTrue(result.allowed);
        assertEquals(null, result.message);
    }

    @Test
    void shouldCreateFailureQuotaCheckResult() {
        QuotaCheckResult result = QuotaCheckResult.failure("Test error message");

        assertFalse(result.allowed);
        assertEquals("Test error message", result.message);
    }

    // ===== Tests pour RateLimitResult =====

    @Test
    void shouldCreateAllowedRateLimitResult() {
        RateLimitResult result = RateLimitResult.allowed();

        assertTrue(result.allowed);
        assertFalse(result.throttled);
        assertEquals(null, result.message);
    }

    @Test
    void shouldCreateThrottledRateLimitResult() {
        RateLimitResult result = RateLimitResult.throttled("Throttle message");

        assertTrue(result.allowed);
        assertTrue(result.throttled);
        assertEquals("Throttle message", result.message);
    }

    @Test
    void shouldCreateRateLimitedResult() {
        RateLimitResult result = RateLimitResult.rateLimited("Rate limit message");

        assertFalse(result.allowed);
        assertFalse(result.throttled);
        assertEquals("Rate limit message", result.message);
    }

    // ===== Tests d'intégration =====

    @Test
    void shouldHandleCompleteUserLifecycle() {
        String userId = "user-lifecycle";
        
        // Add symbols
        service.updateSymbolQuota(userId, 5);
        QuotaCheckResult symbolCheck1 = service.checkSymbolQuota(userId, 5);
        assertTrue(symbolCheck1.allowed);
        
        // Add connections
        service.incrementConnectionQuota(userId);
        service.incrementConnectionQuota(userId);
        QuotaCheckResult connectionCheck1 = service.checkConnectionQuota(userId);
        assertTrue(connectionCheck1.allowed);
        
        // Remove user data
        service.removeUserData(userId);
        
        // Should start fresh
        QuotaCheckResult symbolCheck2 = service.checkSymbolQuota(userId, 10);
        assertTrue(symbolCheck2.allowed);
    }

    @Test
    void shouldHandleCompleteSessionLifecycle() {
        String sessionId = "session-lifecycle";
        
        // Make requests up to throttle threshold
        for (int i = 0; i < 48; i++) {
            service.checkRateLimit(sessionId);
        }
        
        RateLimitResult check1 = service.checkRateLimit(sessionId);
        assertTrue(check1.throttled);
        
        // Remove session data
        service.removeSessionData(sessionId);
        
        // Should start fresh
        RateLimitResult check2 = service.checkRateLimit(sessionId);
        assertFalse(check2.throttled);
    }
}
