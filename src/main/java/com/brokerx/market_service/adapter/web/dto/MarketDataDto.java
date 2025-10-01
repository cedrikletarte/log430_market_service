package com.brokerx.market_service.adapter.web.dto;

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
public class MarketDataDto {

    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal spread;
    private BigDecimal midPrice;
    private Long volume;
    private LocalDateTime timestamp;
    private String status; // "live", "delayed", "stale"
}