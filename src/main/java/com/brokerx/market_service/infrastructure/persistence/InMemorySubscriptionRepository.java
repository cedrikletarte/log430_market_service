package com.brokerx.market_service.infrastructure.persistence;

import com.brokerx.market_service.application.port.out.SubscriptionRepositoryaPort;
import com.brokerx.market_service.domain.model.MarketSubscription;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the subscription repository
 */
@Repository
public class InMemorySubscriptionRepository implements SubscriptionRepositoryaPort {
    
    private final Map<String, MarketSubscription> subscriptions = new ConcurrentHashMap<>();
    
    @Override
    public MarketSubscription save(MarketSubscription subscription) {
        subscriptions.put(subscription.getSessionId(), subscription);
        return subscription;
    }
    
    @Override
    public Optional<MarketSubscription> findBySessionId(String sessionId) {
        return Optional.ofNullable(subscriptions.get(sessionId));
    }
    
    @Override
    public void deleteBySessionId(String sessionId) {
        subscriptions.remove(sessionId);
    }
    
    @Override
    public Set<MarketSubscription> findAllActive() {
        return subscriptions.values().stream()
                .filter(MarketSubscription::isValid)
                .collect(Collectors.toSet());
    }
    
    @Override
    public Set<MarketSubscription> findAll() {
        return Set.copyOf(subscriptions.values());
    }
    
    @Override
    public long countActive() {
        return subscriptions.values().stream()
                .filter(MarketSubscription::isValid)
                .count();
    }
}
