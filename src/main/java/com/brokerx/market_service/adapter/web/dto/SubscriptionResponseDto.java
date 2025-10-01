package com.brokerx.market_service.adapter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponseDto {

    private String type; // "subscription_success", "subscription_error", "market_data", "error"
    private String message;
    private Object data; // Can contain MarketDataDto or other information
    private String timestamp;
}