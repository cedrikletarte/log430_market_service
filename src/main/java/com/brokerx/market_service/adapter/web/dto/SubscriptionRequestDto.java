package com.brokerx.market_service.adapter.web.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRequestDto {

    private String action; // "subscribe", "unsubscribe"
    private Set<String> symbols;
    private String userId;
}