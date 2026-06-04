package com.cambofreelance.apigateway.configs;

import com.cambofreelance.apigateway.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Order(-200)
@RequiredArgsConstructor
public class AdminAuthWebFilter implements WebFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/management/admin/auth/login",
        "/api/management/admin/auth/refresh"
    );

    private static final Set<String> AUTH_PATHS = Set.of(
        "/api/management/admin/auth/login",
        "/api/management/admin/auth/refresh"
    );

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // 200 req/min for general admin endpoints; 10 req/min for auth endpoints
    @Value("${security.admin.rate-limit.general:200}")
    private int generalRateLimit;

    @Value("${security.admin.rate-limit.auth:10}")
    private int authRateLimit;

    private final JwtUtils            jwtUtils;
    private final ObjectMapper        objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith("/api/management/admin/")) {
            return chain.filter(exchange);
        }

        String clientIp = resolveIp(exchange);
        int limit = AUTH_PATHS.contains(path) ? authRateLimit : generalRateLimit;

        return checkRateLimit(clientIp, path, limit)
            .flatMap(exceeded -> {
                if (exceeded) {
                    log.warn("Admin rate limit exceeded: ip={} path={}", clientIp, path);
                    return deny(exchange, HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
                }
                return authenticate(exchange, chain, path);
            });
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }

        String token = header.substring(7);
        if (!jwtUtils.validateJwtToken(token) || !jwtUtils.isAdminToken(token)) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired admin token");
        }

        String username    = jwtUtils.getUserIdFromJwtToken(token);
        Long   userId      = jwtUtils.getUserNumericIdFromToken(token);
        List<String> roles = jwtUtils.getRolesFromToken(token);
        List<String> perms = jwtUtils.getPermissionsFromToken(token);

        exchange.getAttributes().put("adminUser",        username);
        exchange.getAttributes().put("adminUserId",      userId);
        exchange.getAttributes().put("adminRoles",       roles);
        exchange.getAttributes().put("adminPermissions", perms);

        ServerWebExchange mutated = exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header("X-Admin-User", username)
                .build())
            .build();

        return chain.filter(mutated);
    }

    // ── Per-IP rate limiting ───────────────────────────────────────────────────

    private Mono<Boolean> checkRateLimit(String ip, String path, int limit) {
        String bucket = AUTH_PATHS.contains(path) ? "auth" : "admin";
        String key    = bucket + ":rate:" + ip + ":" + LocalDateTime.now().format(MINUTE_FMT);

        return Mono.fromCallable(() -> {
            try {
                Long count = stringRedisTemplate.opsForValue().increment(key);
                if (Long.valueOf(1L).equals(count)) {
                    stringRedisTemplate.expire(key, Duration.ofMinutes(2));
                }
                return count != null && count > limit;
            } catch (Exception e) {
                log.warn("Admin rate limit check failed (fail-open): {}", e.getMessage());
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String resolveIp(ServerWebExchange exchange) {
        // Use rightmost XFF entry (infrastructure-added) to prevent client spoofing
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] ips = forwarded.split(",");
            return ips[ips.length - 1].trim();
        }
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                Map.of("code", "ERR_0" + status.value(), "message", message, "success", false));
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }
}
