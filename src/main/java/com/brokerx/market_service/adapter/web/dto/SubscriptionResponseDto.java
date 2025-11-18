package com.brokerx.market_service.adapter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* DTO for WebSocket Subscription Responses */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponseDto {

    private String type;
    private String message;
    private Object data;
    private String timestamp;
}