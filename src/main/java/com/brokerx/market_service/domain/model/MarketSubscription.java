package com.brokerx.market_service.domain.model;

import java.time.LocalDateTime;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSubscription {
    
    private String sessionId;
    private String userId;
    private Set<String> subscribedSymbols;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private boolean isActive;
    
    public boolean isValid() {
        if (!isActive) {
            return false;
        }
        // Timeout after 5 minutes of inactivity
        return lastActivity.isAfter(LocalDateTime.now().minusMinutes(5));
    }
    
    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
}