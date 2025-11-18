package com.brokerx.market_service.adapter.web.dto;

import java.math.BigDecimal;

/* Response DTO for Stock Information */
public record StockResponse(
    Long id,
    String symbol,
    String name,
    BigDecimal currentPrice
) {}
