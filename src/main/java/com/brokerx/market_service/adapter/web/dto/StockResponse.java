package com.brokerx.market_service.adapter.web.dto;

import java.math.BigDecimal;

public record StockResponse(
    Long id,
    String symbol,
    String name,
    BigDecimal currentPrice
) {}
