package com.cambofreelance.apigateway.caches;

import com.cambofreelance.apigateway.dto.ApiRouteDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redis-backed route cache for O(1) exact-path lookups.
 * Path-variable and wildcard routes are intentionally excluded — they cannot
 * be looked up by exact key, so they are handled exclusively by the
 * in-memory {@link ApiRouteManagerCache}.
 */
@Slf4j
@Component
public class ApiRouteManagerRedisCache {

    @Value("${storage.redis.key-api-route}")
    private String keyValue;

    private String KEY;
    private ReactiveHashOperations<String, String, ApiRouteDto> hashOps;

    private final ReactiveRedisTemplate<String, ApiRouteDto> reactiveRedisTemplate;

    public ApiRouteManagerRedisCache(
            @Qualifier("apiRouteDtoReactiveRedisTemplate")
            ReactiveRedisTemplate<String, ApiRouteDto> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    @PostConstruct
    public void init() {
        KEY = keyValue;
        hashOps = reactiveRedisTemplate.opsForHash();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    public void initCache(List<ApiRouteDto> routes) {
        Map<String, ApiRouteDto> exactRoutes = routes.stream()
            .filter(r -> !hasPathVariable(r.getPath()) && !r.getPath().endsWith("/**"))
            .collect(Collectors.toMap(
                r -> buildKey(r.getPath(), r.getMethod()),
                r -> r,
                (a, b) -> b
            ));

        reactiveRedisTemplate.delete(KEY)
            .then(exactRoutes.isEmpty() ? Mono.just(Boolean.TRUE) : hashOps.putAll(KEY, exactRoutes))
            .subscribe(
                null,
                e -> log.error("Route Redis cache init error: {}", e.getMessage()),
                () -> log.info("Route Redis cache initialized: {} exact routes", exactRoutes.size())
            );
    }

    // ── Get — O(1) exact match only ────────────────────────────────────────────

    public Mono<ApiRouteDto> get(String path, String method) {
        return hashOps.get(KEY, buildKey(path, method))
            .switchIfEmpty(hashOps.get(KEY, buildKey(path, "")));
    }

    // ── Put / Evict ────────────────────────────────────────────────────────────

    public void put(ApiRouteDto route) {
        if (hasPathVariable(route.getPath()) || route.getPath().endsWith("/**")) {
            return;
        }
        hashOps.put(KEY, buildKey(route.getPath(), route.getMethod()), route)
            .subscribe(null, e -> log.warn("Route Redis cache put error: {}", e.getMessage()));
    }

    public void evict(String path, String method) {
        if (hasPathVariable(path) || path.endsWith("/**")) {
            return;
        }
        hashOps.remove(KEY, (Object) buildKey(path, method))
            .subscribe(null, e -> log.warn("Route Redis cache evict error: {}", e.getMessage()));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildKey(String path, String method) {
        return path + ":" + (method != null ? method.toUpperCase() : "");
    }

    private boolean hasPathVariable(String path) {
        return path != null && path.contains("{");
    }
}
