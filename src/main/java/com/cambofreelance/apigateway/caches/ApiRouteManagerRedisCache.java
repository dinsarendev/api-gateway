package com.cambofreelance.apigateway.caches;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class ApiRouteManagerRedisCache {

    @Value("${storage.redis.key-api-route}")
    private String keyValue;

    private String KEY;

    private HashOperations<String, String, ApiRouteDto> hashOperations;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void init() {
        try {
            KEY = keyValue;
            hashOperations = redisTemplate.opsForHash();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis init failed", e);
        }
    }

    // ================= INIT =================
    public void initCache(List<ApiRouteDto> routes) {
        try {
            redisTemplate.delete(KEY);

            for (ApiRouteDto route : routes) {
                hashOperations.put(KEY, buildKey(route.getPath(), route.getMethod()), route);

                // Track wildcard routes in a separate set for efficient lookup
                if (route.getPath().endsWith("/**")) {
                    hashOperations.put(KEY + ":wildcards", buildKey(route.getPath(), route.getMethod()), route);
                }
            }

            log.info("API Route cache initialized: {} records", routes.size());

        } catch (Exception e) {
            log.error("Init cache error", e);
        }
    }

    // ================= ADD / UPDATE =================
    public void put(ApiRouteDto route) {
        try {
            hashOperations.put(KEY, buildKey(route.getPath(), route.getMethod()), route);

            if (route.getPath().endsWith("/**")) {
                hashOperations.put(KEY + ":wildcards", buildKey(route.getPath(), route.getMethod()), route);
            }

        } catch (Exception e) {
            log.error("Put cache error", e);
        }
    }

    public void evict(ApiRouteDto route) {
        try {
            String redisKey = buildKey(route.getPath(), route.getMethod());
            hashOperations.delete(KEY, redisKey);

            // Also remove from wildcard set if applicable
            if (route.getPath().endsWith("/**")) {
                hashOperations.delete(KEY + ":wildcards", redisKey);
            }

        } catch (Exception e) {
            log.error("Evict cache error", e);
        }
    }

    // ================= GET =================
    public ApiRouteDto get(String path, String method) {
        try {
            // 1. Exact match O(1)
            ApiRouteDto exact = hashOperations.get(KEY, buildKey(path, method));
            if (exact != null) {
                return exact;
            }

            // 2. Wildcard match — only scans the smaller wildcards hash, not full route table
            Map<String, ApiRouteDto> wildcards = hashOperations.entries(KEY + ":wildcards");

            return wildcards.values().stream()
                .filter(dto -> dto.getMethod().equalsIgnoreCase(method))
                .filter(dto -> matchesWildcard(dto.getPath(), path))
                .findFirst()
                .orElse(null);

        } catch (Exception e) {
            log.error("Get cache error", e);
            return null;
        }
    }

    // ================= UTIL =================

    /**
     * Builds a composite Redis hash field key from path and HTTP method.
     * Example: "/authentication/applications/**" + "GET" → "/authentication/applications/**:GET"
     */
    private String buildKey(String path, String method) {
        return path + ":" + method.toUpperCase();
    }

    /**
     * Matches a request path against a wildcard route pattern (/** suffix only).
     * Requires a "/" boundary after the prefix to prevent false matches.
     *
     * Examples:
     *   matchesWildcard("/api/users/**", "/api/users/123")     → true
     *   matchesWildcard("/api/users/**", "/api/users")         → true  (exact prefix)
     *   matchesWildcard("/api/users/**", "/api/usersfoo")      → false (no boundary)
     */
    private boolean matchesWildcard(String routePath, String requestPath) {
        if (!routePath.endsWith("/**")) {
            return false;
        }
        String prefix = routePath.replace("/**", "");
        return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
    }
}