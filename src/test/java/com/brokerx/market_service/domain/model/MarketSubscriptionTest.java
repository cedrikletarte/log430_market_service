package com.brokerx.market_service.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MarketSubscriptionTest {

    @Test
    void shouldCreateSubscriptionWithBuilder() {
        LocalDateTime now = LocalDateTime.now();
        Set<String> symbols = Set.of("AAPL", "GOOGL", "TSLA");

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(symbols)
                .createdAt(now)
                .lastActivity(now)
                .isActive(true)
                .build();

        assertNotNull(subscription);
        assertEquals("session-123", subscription.getSessionId());
        assertEquals("user-456", subscription.getUserId());
        assertEquals(3, subscription.getSubscribedSymbols().size());
        assertTrue(subscription.getSubscribedSymbols().contains("AAPL"));
        assertEquals(now, subscription.getCreatedAt());
        assertEquals(now, subscription.getLastActivity());
        assertTrue(subscription.isActive());
    }

    @Test
    void shouldBeValidWhenActiveAndRecentActivity() {
        LocalDateTime now = LocalDateTime.now();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(now)
                .lastActivity(now)
                .isActive(true)
                .build();

        assertTrue(subscription.isValid());
    }

    @Test
    void shouldBeInvalidWhenInactive() {
        LocalDateTime now = LocalDateTime.now();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(now)
                .lastActivity(now)
                .isActive(false)
                .build();

        assertFalse(subscription.isValid());
    }

    @Test
    void shouldBeInvalidAfterFiveMinutesOfInactivity() {
        LocalDateTime sixMinutesAgo = LocalDateTime.now().minusMinutes(6);

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(sixMinutesAgo)
                .lastActivity(sixMinutesAgo)
                .isActive(true)
                .build();

        assertFalse(subscription.isValid());
    }

    @Test
    void shouldBeValidJustBeforeFiveMinuteTimeout() {
        LocalDateTime fourMinutesAgo = LocalDateTime.now().minusMinutes(4).minusSeconds(30);

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(fourMinutesAgo)
                .lastActivity(fourMinutesAgo)
                .isActive(true)
                .build();

        assertTrue(subscription.isValid());
    }

    @Test
    void shouldUpdateActivityToCurrentTime() throws InterruptedException {
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(tenMinutesAgo)
                .lastActivity(tenMinutesAgo)
                .isActive(true)
                .build();

        // Initially invalid due to timeout
        assertFalse(subscription.isValid());

        // Petit délai pour s'assurer que le temps change
        Thread.sleep(10);

        // Update activity
        subscription.updateActivity();

        // Now should be valid
        assertTrue(subscription.isValid());
        assertTrue(subscription.getLastActivity().isAfter(tenMinutesAgo));
    }

    @Test
    void shouldHandleEmptySymbolSet() {
        LocalDateTime now = LocalDateTime.now();
        Set<String> emptySymbols = new HashSet<>();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(emptySymbols)
                .createdAt(now)
                .lastActivity(now)
                .isActive(true)
                .build();

        assertTrue(subscription.isValid());
        assertEquals(0, subscription.getSubscribedSymbols().size());
    }

    @Test
    void shouldHandleMultipleSymbols() {
        LocalDateTime now = LocalDateTime.now();
        Set<String> symbols = Set.of("AAPL", "GOOGL", "TSLA", "MSFT", "AMZN", "META", "NVDA", "NFLX");

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(symbols)
                .createdAt(now)
                .lastActivity(now)
                .isActive(true)
                .build();

        assertTrue(subscription.isValid());
        assertEquals(8, subscription.getSubscribedSymbols().size());
    }

    @Test
    void shouldUpdateActivityMultipleTimes() throws InterruptedException {
        LocalDateTime initialTime = LocalDateTime.now();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(initialTime)
                .lastActivity(initialTime)
                .isActive(true)
                .build();

        Thread.sleep(10);
        subscription.updateActivity();
        LocalDateTime firstUpdate = subscription.getLastActivity();

        Thread.sleep(10);
        subscription.updateActivity();
        LocalDateTime secondUpdate = subscription.getLastActivity();

        assertTrue(firstUpdate.isAfter(initialTime));
        assertTrue(secondUpdate.isAfter(firstUpdate));
        assertTrue(subscription.isValid());
    }

    @Test
    void shouldBeInvalidWhenInactiveEvenWithRecentActivity() {
        LocalDateTime now = LocalDateTime.now();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(now)
                .lastActivity(now)
                .isActive(false)
                .build();

        assertFalse(subscription.isValid());
    }

    @Test
    void shouldSetAndGetAllFields() {
        MarketSubscription subscription = new MarketSubscription();
        LocalDateTime now = LocalDateTime.now();
        Set<String> symbols = Set.of("AAPL", "GOOGL");

        subscription.setSessionId("new-session");
        subscription.setUserId("new-user");
        subscription.setSubscribedSymbols(symbols);
        subscription.setCreatedAt(now);
        subscription.setLastActivity(now);
        subscription.setActive(true);

        assertEquals("new-session", subscription.getSessionId());
        assertEquals("new-user", subscription.getUserId());
        assertEquals(2, subscription.getSubscribedSymbols().size());
        assertEquals(now, subscription.getCreatedAt());
        assertEquals(now, subscription.getLastActivity());
        assertTrue(subscription.isActive());
    }

    @Test
    void shouldDeactivateSubscription() {
        LocalDateTime now = LocalDateTime.now();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(now)
                .lastActivity(now)
                .isActive(true)
                .build();

        assertTrue(subscription.isValid());

        // Deactivate
        subscription.setActive(false);

        assertFalse(subscription.isValid());
    }

    @Test
    void shouldReactivateSubscription() {
        LocalDateTime now = LocalDateTime.now();

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(now)
                .lastActivity(now)
                .isActive(false)
                .build();

        assertFalse(subscription.isValid());

        // Reactivate with recent activity
        subscription.setActive(true);
        subscription.updateActivity();

        assertTrue(subscription.isValid());
    }

    @Test
    void shouldHandleExactFiveMinuteTimeout() {
        // Exactement 5 minutes - devrait être invalide (isAfter, pas isAfterOrEqual)
        LocalDateTime exactlyFiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        MarketSubscription subscription = MarketSubscription.builder()
                .sessionId("session-123")
                .userId("user-456")
                .subscribedSymbols(Set.of("AAPL"))
                .createdAt(exactlyFiveMinutesAgo)
                .lastActivity(exactlyFiveMinutesAgo)
                .isActive(true)
                .build();

        // À exactement 5 minutes, devrait être invalide car isAfter exclut l'égalité
        assertFalse(subscription.isValid());
    }
}
