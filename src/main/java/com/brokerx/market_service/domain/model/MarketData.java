package com.brokerx.market_service.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    
    private Long id;
    private String symbol;
    private String name;
    private BigDecimal lastPrice;
    private BigDecimal bid;
    private BigDecimal ask;
    private Long volume;
    private LocalDateTime timestamp;
    
    public BigDecimal getSpread() {
        if (ask != null && bid != null) {
            return ask.subtract(bid);
        }
        return BigDecimal.ZERO;
    }
    
    public BigDecimal getMidPrice() {
        if (ask != null && bid != null) {
            return bid.add(ask).divide(BigDecimal.valueOf(2));
        }
        return lastPrice != null ? lastPrice : BigDecimal.ZERO;
    }
}