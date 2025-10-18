package com.brokerx.market_service.application.service;

import com.brokerx.market_service.application.port.in.ManageSubscriptionUseCase;
import com.brokerx.market_service.application.port.in.SubscribeToMarketDataUseCase;
import com.brokerx.market_service.application.port.out.SubscriptionRepositoryaPort;
import com.brokerx.market_service.application.port.out.SymbolSubscriberRepositoryPort;
import com.brokerx.market_service.domain.model.MarketSubscription;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Service for managing market data subscriptions
 * Implements hexagonal architecture with use case ports
 */
@Service
@RequiredArgsConstructor
public class MarketSubscriptionService implements SubscribeToMarketDataUseCase, ManageSubscriptionUseCase {

    private static final Logger logger = LogManager.getLogger(MarketSubscriptionService.class);

    private final SubscriptionRepositoryaPort subscriptionRepository;
    private final SymbolSubscriberRepositoryPort symbolSubscriberRepository;

    @Override
    public MarketSubscription subscribe(String sessionId, String userId, Set<String> symbols) {
        MarketSubscription subscription = subscriptionRepository.findBySessionId(sessionId).orElse(null);

        if (subscription == null) {
            subscription = MarketSubscription.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .subscribedSymbols(new HashSet<>(symbols))
                    .createdAt(LocalDateTime.now())
                    .lastActivity(LocalDateTime.now())
                    .isActive(true)
                    .build();

            subscriptionRepository.save(subscription);
            logger.info("Created new subscription for session: {} with symbols: {}", sessionId, symbols);
        } else {
            // Removes old symbol subscriptions
            symbolSubscriberRepository.removeSubscriberFromSymbols(subscription.getSubscribedSymbols(), sessionId);

            // Updates with new symbols
            subscription.setSubscribedSymbols(new HashSet<>(symbols));
            subscription.updateActivity();
            subscriptionRepository.save(subscription);
            logger.info("Updated subscription for session: {} with symbols: {}", sessionId, symbols);
        }

        // Adds new symbol subscriptions
        symbolSubscriberRepository.addSubscriberToSymbols(symbols, sessionId);

        return subscription;
    }

    @Override
    public void addSymbols(String sessionId, Set<String> symbols) {
        subscriptionRepository.findBySessionId(sessionId).ifPresent(subscription -> {
            if (subscription.isActive()) {
                subscription.getSubscribedSymbols().addAll(symbols);
                subscription.updateActivity();
                subscriptionRepository.save(subscription);
                symbolSubscriberRepository.addSubscriberToSymbols(symbols, sessionId);
                logger.info("Added symbols {} to subscription {}", symbols, sessionId);
            }
        });
    }

    @Override
    public void removeSymbols(String sessionId, Set<String> symbols) {
        subscriptionRepository.findBySessionId(sessionId).ifPresent(subscription -> {
            if (subscription.isActive()) {
                subscription.getSubscribedSymbols().removeAll(symbols);
                subscription.updateActivity();
                subscriptionRepository.save(subscription);
                symbolSubscriberRepository.removeSubscriberFromSymbols(symbols, sessionId);
                logger.info("Removed symbols {} from subscription {}", symbols, sessionId);
            }
        });
    }

    @Override
    public void removeSubscription(String sessionId) {
        subscriptionRepository.findBySessionId(sessionId).ifPresent(subscription -> {
            symbolSubscriberRepository.removeSubscriberFromSymbols(subscription.getSubscribedSymbols(), sessionId);
            subscriptionRepository.deleteBySessionId(sessionId);
            logger.info("Removed subscription for session: {}", sessionId);
        });
    }

    @Override
    public void deactivateSubscription(String sessionId) {
        subscriptionRepository.findBySessionId(sessionId).ifPresent(subscription -> {
            subscription.setActive(false);
            symbolSubscriberRepository.removeSubscriberFromSymbols(subscription.getSubscribedSymbols(), sessionId);
            subscriptionRepository.save(subscription);
            logger.info("Deactivated subscription for session: {}", sessionId);
        });
    }

    @Override
    public MarketSubscription getSubscription(String sessionId) {
        return subscriptionRepository.findBySessionId(sessionId).orElse(null);
    }

    @Override
    public Set<String> getSubscribersForSymbol(String symbol) {
        return symbolSubscriberRepository.getSubscribers(symbol);
    }

    @Override
    public void updateActivity(String sessionId) {
        subscriptionRepository.findBySessionId(sessionId).ifPresent(subscription -> {
            subscription.updateActivity();
            subscriptionRepository.save(subscription);
        });
    }

    @Override
    public void cleanupExpiredSubscriptions() {
        subscriptionRepository.findAll().forEach(subscription -> {
            if (!subscription.isValid()) {
                symbolSubscriberRepository.removeSubscriberFromSymbols(
                        subscription.getSubscribedSymbols(), 
                        subscription.getSessionId()
                );
                subscriptionRepository.deleteBySessionId(subscription.getSessionId());
                logger.info("Cleaned up expired subscription: {}", subscription.getSessionId());
            }
        });
    }

    @Override
    public int getActiveSubscriptionsCount() {
        return (int) subscriptionRepository.countActive();
    }
}