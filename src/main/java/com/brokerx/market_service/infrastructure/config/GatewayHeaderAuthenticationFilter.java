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

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
                
        // Extraire les headers ajoutés par le Gateway
        String userId = request.getHeader(USER_ID_HEADER);
        String email = request.getHeader(USER_EMAIL_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (!validateSignature(signature, userId, email, role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Unauthorized access\"}");
            return;
        }

        System.out.println("Headers reçus: " + USER_ID_HEADER + "=" + userId + ", " +
                USER_EMAIL_HEADER + "=" + email + ", " +
                USER_ROLE_HEADER + "=" + role);

        // Si les headers sont présents, le Gateway a validé le JWT
        if (userId != null && email != null && role != null) {
            
            // Créer une authentification basée sur les headers du Gateway
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            
            var authentication = new UsernamePasswordAuthenticationToken(
                userId,  // Principal = userId (ce que le contrôleur attend)
                null,   // Pas de credentials nécessaires
                authorities
            );
            
            // Ajouter l'email comme détail supplémentaire
            authentication.setDetails(Map.of("userId", userId, "email", email));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("Authentication set from Gateway headers: userId={}, email={}, role={}", 
                userId, email, role);
        }

        System.out.println("Authentication dans le contexte: " + SecurityContextHolder.getContext().getAuthentication());
        System.out.println("Détails de l'authentification: " + SecurityContextHolder.getContext().getAuthentication().getDetails());

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        
        // don't filter public paths and internal service-to-service endpoints
        return path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/ws/market") ||  // Exclure tous les endpoints WebSocket/SockJS
               path.startsWith("/topic/market") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/prometheus") ||
               path.startsWith("/internal");     // Exclure les endpoints internes (service-to-service)
    }

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
