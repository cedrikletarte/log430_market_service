package com.brokerx.market_service.adapter.web.dto;

/* Generic API Response Wrapper */
public record ApiResponse<T>(
    String status,
    String errorCode,
    String message,
    T data
) {}
