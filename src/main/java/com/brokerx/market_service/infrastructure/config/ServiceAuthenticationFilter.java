package com.brokerx.market_service.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter for internal endpoints (/internal/**).
 * Validates that the request comes from another microservice by checking
 * the X-Service-Token header containing a shared secret between services.
 */
@Slf4j
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    @Value("${service.secret}")
    private String serviceSecret;

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String serviceToken = request.getHeader(SERVICE_TOKEN_HEADER);

        // Check if the service token is valid
        if (serviceToken == null || !serviceToken.equals(serviceSecret)) {
            log.warn("Invalid or missing service token for internal endpoint: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Access denied - Invalid service token\"}");
            return;
        }

        log.debug("Valid service token for internal endpoint: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        // This filter applies ONLY to /internal/** endpoints
        return !path.startsWith("/internal/");
    }
}
