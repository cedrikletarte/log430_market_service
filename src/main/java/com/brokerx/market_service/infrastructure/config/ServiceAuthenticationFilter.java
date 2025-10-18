package com.brokerx.market_service.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
 * a JWT signature in the X-Service-Token header.
 */
@Slf4j
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    @Value("${service.secret}")
    private String serviceSecret;

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final long MAX_SIGNATURE_AGE_MS = 5000; // 5 seconds

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String serviceToken = request.getHeader(SERVICE_TOKEN_HEADER);

        if (serviceToken == null || serviceToken.isEmpty()) {
            log.warn("Missing service token for internal endpoint: {}", request.getRequestURI());
            sendErrorResponse(response, "Access denied - Missing service token");
            return;
        }

        if (!validateSignature(serviceToken)) {
            log.warn("Invalid service token for internal endpoint: {}", request.getRequestURI());
            sendErrorResponse(response, "Access denied - Invalid service token");
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

    /**
     * Validates the JWT signature from another service.
     * Checks:
     * 1. Signature is valid (signed with service.secret)
     * 2. Signature is not too old (replay attack prevention)
     */
    private boolean validateSignature(String signature) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(serviceSecret.getBytes()))
                .build()
                .parseSignedClaims(signature)
                .getPayload();
            
            // Extract timestamp
            Long timestamp = claims.get("timestamp", Long.class);
            if (timestamp == null) {
                log.warn("Service token missing timestamp");
                return false;
            }
            
            // Check if signature is too old (replay attack prevention)
            long age = System.currentTimeMillis() - timestamp;
            if (age > MAX_SIGNATURE_AGE_MS) {
                log.warn("Service token too old: {} ms", age);
                return false;
            }
            
            // Extract service name for logging
            String serviceName = claims.get("service", String.class);
            log.debug("Valid service token from: {}", serviceName);
            
            return true;
        } catch (Exception e) {
            log.error("Invalid service signature: {}", e.getMessage());
            return false;
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"status\":\"ERROR\",\"errorCode\":\"FORBIDDEN\",\"message\":\"%s\",\"data\":null}",
            message
        ));
    }
}
