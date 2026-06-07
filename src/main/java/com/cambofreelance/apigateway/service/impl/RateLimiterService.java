package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Slf4j
@Service
public class RateLimiterService {

    private static final String LUA_SCRIPT =
        "local key = KEYS[1] " +
        "local limit = tonumber(ARGV[1]) " +
        "local window = tonumber(ARGV[2]) " +
        "local current = redis.call('INCR', key) " +
        "if current == 1 then " +
        "    redis.call('EXPIRE', key, window) " +
        "end " +
        "if current > limit then " +
        "    return 0 " +
        "else " +
        "    return 1 " +
        "end";

    private static final RedisScript<Long> REDIS_SCRIPT = RedisScript.of(LUA_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiterService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Checks whether the request is within the rate limit for its route.
     *
     * Uses the route's own path pattern as the Redis key bucket so that all
     * requests matching the same route (e.g. /users/123 and /users/456 both
     * matching /users/{id}) share one counter per client IP.
     *
     * Fail-open: if Redis is unavailable, the request is allowed through.
     */
    public Mono<Boolean> isAllowed(String method, String clientIp, ApiRouteDto routeConfig) {
        if (routeConfig == null || routeConfig.getRateLimit() == null) {
            return Mono.just(true);
        }

        String safeIp = (clientIp != null && !clientIp.isEmpty()) ? clientIp : "unknown-ip";
        String scope  = Constants.YES.equalsIgnoreCase(routeConfig.getIsPublic()) ? "public" : "private";
        String route  = routePatternKey(routeConfig.getPath());

        // key: rate_limit:<scope>:<ip>:<route-pattern>:<METHOD>
        String redisKey = String.format("rate_limit:%s:%s:%s:%s", scope, safeIp, route, method.toUpperCase());

        log.debug("Rate limit check: key={}, limit={}, window={}s",
            redisKey, routeConfig.getRateLimit(), routeConfig.getRateLimitDuration());

        return Mono.fromCallable(() -> stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Collections.singletonList(redisKey),
                routeConfig.getRateLimit().toString(),
                routeConfig.getRateLimitDuration().toString()))
            .map(result -> result != null && result == 1L)
            .onErrorResume(ex -> {
                log.error("Rate limiter Redis error (fail-open): {}", ex.getMessage());
                return Mono.just(true);
            });
    }

    /**
     * Strips the /** suffix from wildcard route patterns so that routes
     * resolved from in-memory cache (/api) and Redis cache (/api/**) produce
     * the same rate limit key.
     */
    private String routePatternKey(String path) {
        if (path == null) return "/";
        return path.endsWith("/**") ? path.substring(0, path.length() - 3) : path;
    }
}
