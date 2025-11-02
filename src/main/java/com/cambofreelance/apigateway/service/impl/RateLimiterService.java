package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.caches.ApiRouteManagerRedisCache;
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

    // Reuse Redis script instead of recreating each call
    private static final RedisScript<Long> REDIS_SCRIPT =
        RedisScript.of(LUA_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ApiRouteManagerRedisCache apiRouteManagerRedisCache;

    public RateLimiterService(StringRedisTemplate stringRedisTemplate,
        ApiRouteManagerRedisCache apiRouteManagerRedisCache) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.apiRouteManagerRedisCache = apiRouteManagerRedisCache;
    }

    /**
     * g Check if request is allowed based on: - Public API -> use clientIp - Private API -> use
     * userId + deviceId
     */
//    public Mono<Boolean> isAllowed(String path,
//                                   String method,
//                                   String userId,
//                                   String deviceId,
//                                   String clientIp) {
//
//        final String normalizedPath = normalizePath(path);
//        ApiRouteDto routeConfig = apiRouteManagerRedisCache.getPathAndMethod(normalizedPath, method);
//
//        if (routeConfig == null || routeConfig.getRateLimit() == null) {
//            return Mono.just(true); // no rate limit configured
//        }
//
//        String safeUserId;
//        String safeDeviceId;
//
//        if (Constants.YES.equals(routeConfig.getIsPublic())) {
//            // Public API -> fallback to IP + deviceId
//            safeUserId = "public";
//            // Use Device-Id header if present; fallback to IP
//            safeDeviceId = (deviceId != null && !deviceId.isEmpty())
//                ? deviceId
//                : (clientIp != null && !clientIp.isEmpty() ? clientIp : "unknown-device");
//        } else {
//            // Private API -> use userId + deviceId
//            safeUserId = (userId != null && !userId.isEmpty()) ? userId : "anonymous";
//            safeDeviceId = (deviceId != null && !deviceId.isEmpty()) ? deviceId : "unknown";
//        }
//
//        // Redis key format: rate_limit:user/device OR rate_limit:public:ip
//        String redisKey = String.format("rate_limit:%s:%s:%s:%s",
//                safeUserId, safeDeviceId, normalizedPath, method);
//
//        log.info("Rate limiting -> path={}, method={}, userId={}, deviceId={}, redisKey={}",
//                path, method, safeUserId, safeDeviceId, redisKey);
//
//        return Mono.fromCallable(() -> stringRedisTemplate.execute(
//                        REDIS_SCRIPT,
//                        Collections.singletonList(redisKey),
//                        routeConfig.getRateLimit().toString(),
//                        routeConfig.getRateLimitDuration().toString()
//                ))
//                .map(result -> result != null && result == 1)
//                .onErrorResume(ex -> {
//                    log.error("Rate limiter Redis error: {}", ex.getMessage(), ex);
//                    return Mono.just(true); // fail-open, can change to false if stricter
//                });
//    }
    public Mono<Boolean> isAllowed(String path, String method, String clientIp) {

        final String normalizedPath = normalizePath(path);
        ApiRouteDto routeConfig = apiRouteManagerRedisCache.getPathAndMethod(normalizedPath,
            method);

        // No rate limit configured → allow request
        if (routeConfig == null || routeConfig.getRateLimit() == null) {
            return Mono.just(true);
        }

        // ✅ Always apply rate limit per IP + route
        String safeClientIp = (clientIp != null && !clientIp.isEmpty()) ? clientIp : "unknown-ip";

        // Optional: separate public/private route namespaces
        String rateScope = Constants.YES.equals(routeConfig.getIsPublic()) ? "public" : "private";

        // ✅ Redis key: rate_limit:<scope>:<ip>:<path>:<method>
        String redisKey = String.format("rate_limit:%s:%s:%s:%s",
            rateScope, safeClientIp, normalizedPath, method);

        log.info("Rate limiting -> path={}, method={}, clientIp={}, redisKey={}",
            path, method, safeClientIp, redisKey);

        // Execute rate limit Lua script
        return Mono.fromCallable(() -> stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Collections.singletonList(redisKey),
                routeConfig.getRateLimit().toString(),
                routeConfig.getRateLimitDuration().toString()
            ))
            .map(result -> result != null && result == 1)
            .onErrorResume(ex -> {
                log.error("Rate limiter Redis error: {}", ex.getMessage(), ex);
                return Mono.just(true); // fail-open mode (can be false for strict mode)
            });
    }


    public String normalizePath(String path) {
        path = path.replaceAll("/\\d+", "/{id}"); // numeric IDs
        path = path.replaceAll("/[0-9a-fA-F\\-]{36}", "/{id}"); // UUIDs
        path = path.replaceAll("/[a-zA-Z0-9]{8,}", "/{id}"); // generic long IDs
        return path;
    }

}
