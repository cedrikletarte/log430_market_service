package com.brokerx.market_service.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String SIGNATURE_HEADER = "X-Gateway-Secret";

    /* Filter to authenticate requests based on Gateway headers */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
                
        // Extract headers
        String userId = request.getHeader(USER_ID_HEADER);
        String email = request.getHeader(USER_EMAIL_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (!validateSignature(signature, userId, email, role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Unauthorized access\"}");
            return;
        }

        // If headers are present, the Gateway has validated the JWT
        if (userId != null && email != null && role != null) {
            
            // Create authentication token
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            
            var authentication = new UsernamePasswordAuthenticationToken(
                userId,  // Principal = userId
                null,   // No credentials needed
                authorities
            );
            
            // Add email as additional detail
            authentication.setDetails(Map.of("userId", userId, "email", email));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("Authentication set from Gateway headers: userId={}, email={}, role={}", 
                userId, email, role);
        }

        filterChain.doFilter(request, response);
    }

    /* Determine if the filter should not apply to the request */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        
        // don't filter public paths and internal service-to-service endpoints
        return path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/ws/market") ||  // Exclude all WebSocket/SockJS endpoints
               path.startsWith("/topic/market") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/prometheus") ||
               path.startsWith("/internal");     // Exclude internal service-to-service endpoints
    }

    /* Validates the gateway signature */
    private boolean validateSignature(String signature, String userId, String email, String role) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(gatewaySecret.getBytes()))
                .build()
                .parseSignedClaims(signature)
                .getPayload();
            
            String data = claims.get("data", String.class);
            return data.startsWith(userId + ":" + email + ":" + role);
        } catch (Exception e) {
            log.error("Invalid gateway signature", e);
            return false;
        }
    }
}
