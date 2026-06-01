package com.cambofreelance.apigateway.configs;

import com.cambofreelance.apigateway.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Order(-200)
@RequiredArgsConstructor
public class AdminAuthWebFilter implements WebFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/admin/auth/login",
        "/admin/auth/refresh"
    );

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only intercept /admin/** — let static assets and proxied routes through
        if (!path.startsWith("/admin/")) {
            return chain.filter(exchange);
        }

        // Public admin endpoints need no token
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return deny(exchange, "Missing Authorization header");
        }

        String token = header.substring(7);
        if (!jwtUtils.validateJwtToken(token) || !jwtUtils.isAdminToken(token)) {
            return deny(exchange, "Invalid or expired admin token");
        }

        // Propagate username downstream for audit use
        String username = jwtUtils.getUserIdFromJwtToken(token);
        ServerWebExchange mutated = exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header("X-Admin-User", username)
                .build())
            .build();

        return chain.filter(mutated);
    }

    private Mono<Void> deny(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                Map.of("code", "ERR_00401", "message", message, "success", false));
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }
}