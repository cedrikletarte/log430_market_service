package com.brokerx.market_service.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for enabling asynchronous processing and scheduling
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}