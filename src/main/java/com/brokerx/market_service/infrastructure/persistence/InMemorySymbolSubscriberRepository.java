package com.brokerx.market_service.infrastructure.persistence;

import com.brokerx.market_service.application.port.out.SymbolSubscriberRepositoryPort;

import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the symbol-subscriber repository
 */
@Repository
public class InMemorySymbolSubscriberRepository implements SymbolSubscriberRepositoryPort {
    
    private final Map<String, Set<String>> symbolSubscribers = new ConcurrentHashMap<>();
    
    @Override
    public void addSubscriber(String symbol, String sessionId) {
        String upperSymbol = symbol.toUpperCase();
        symbolSubscribers.computeIfAbsent(upperSymbol, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }
    
    @Override
    public void removeSubscriber(String symbol, String sessionId) {
        String upperSymbol = symbol.toUpperCase();
        Set<String> subscribers = symbolSubscribers.get(upperSymbol);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            if (subscribers.isEmpty()) {
                symbolSubscribers.remove(upperSymbol);
            }
        }
    }
    
    @Override
    public Set<String> getSubscribers(String symbol) {
        return symbolSubscribers.getOrDefault(symbol.toUpperCase(), new HashSet<>());
    }
    
    @Override
    public void addSubscriberToSymbols(Set<String> symbols, String sessionId) {
        symbols.forEach(symbol -> addSubscriber(symbol, sessionId));
    }
    
    @Override
    public void removeSubscriberFromSymbols(Set<String> symbols, String sessionId) {
        symbols.forEach(symbol -> removeSubscriber(symbol, sessionId));
    }
}
