package com.cambofreelance.apigateway.caches;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Comparator;
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

    @Resource(name = "apiRouteDtoRedisTemplate")
    private RedisTemplate<String, ApiRouteDto> redisTemplate;

    @PostConstruct
    public void init() {
        try {
            KEY = keyValue;
            hashOperations = redisTemplate.opsForHash();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis init failed", e);
        }
    }

    private static final String SUFFIX_WILDCARDS = ":wildcards";
    private static final String SUFFIX_PATH_VARS = ":path-variables";

    // ================= INIT =================
    public void initCache(List<ApiRouteDto> routes) {
        try {
            redisTemplate.delete(KEY);
            redisTemplate.delete(KEY + SUFFIX_WILDCARDS);
            redisTemplate.delete(KEY + SUFFIX_PATH_VARS);

            for (ApiRouteDto route : routes) {
                String hashKey = buildKey(route.getPath(), route.getMethod());
                hashOperations.put(KEY, hashKey, route);

                if (hasPathVariable(route.getPath())) {
                    hashOperations.put(KEY + SUFFIX_PATH_VARS, hashKey, route);
                } else if (route.getPath().endsWith("/**")) {
                    hashOperations.put(KEY + SUFFIX_WILDCARDS, hashKey, route);
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
            String hashKey = buildKey(route.getPath(), route.getMethod());
            hashOperations.put(KEY, hashKey, route);

            if (hasPathVariable(route.getPath())) {
                hashOperations.put(KEY + SUFFIX_PATH_VARS, hashKey, route);
            } else if (route.getPath().endsWith("/**")) {
                hashOperations.put(KEY + SUFFIX_WILDCARDS, hashKey, route);
            }

        } catch (Exception e) {
            log.error("Put cache error", e);
        }
    }

    public void evict(ApiRouteDto route) {
        try {
            String hashKey = buildKey(route.getPath(), route.getMethod());
            hashOperations.delete(KEY, hashKey);

            if (hasPathVariable(route.getPath())) {
                hashOperations.delete(KEY + SUFFIX_PATH_VARS, hashKey);
            } else if (route.getPath().endsWith("/**")) {
                hashOperations.delete(KEY + SUFFIX_WILDCARDS, hashKey);
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

            // 2. Path variable match: /users/{id}, /items/{id}/details
            Map<String, ApiRouteDto> pathVars = hashOperations.entries(KEY + SUFFIX_PATH_VARS);
            ApiRouteDto pathVarMatch = pathVars.values().stream()
                .filter(dto -> dto.getMethod().equalsIgnoreCase(method))
                .filter(dto -> matchesPathVariable(dto.getPath(), path))
                .max(Comparator.comparingInt(dto -> countLiteralSegments(dto.getPath())))
                .orElse(null);
            if (pathVarMatch != null) {
                return pathVarMatch;
            }

            // 3. Wildcard match: /api/**
            Map<String, ApiRouteDto> wildcards = hashOperations.entries(KEY + SUFFIX_WILDCARDS);
            return wildcards.values().stream()
                .filter(dto -> dto.getMethod().equalsIgnoreCase(method))
                .filter(dto -> matchesWildcard(dto.getPath(), path))
                .max(Comparator.comparingInt(dto -> dto.getPath().length()))
                .orElse(null);

        } catch (Exception e) {
            log.error("Get cache error", e);
            return null;
        }
    }

    // ================= UTIL =================

    private String buildKey(String path, String method) {
        return path + ":" + method.toUpperCase();
    }

    private boolean hasPathVariable(String path) {
        return path != null && path.contains("{");
    }

    /**
     * Matches a request path against a path variable pattern.
     * Segments in {curly braces} match any single path segment.
     * A ** segment matches all remaining segments.
     *
     * Examples:
     *   /users/{id}           vs /users/123        → true
     *   /users/{id}/posts/**  vs /users/123/posts/1 → true
     *   /users/{id}           vs /users/123/extra   → false
     */
    private boolean matchesPathVariable(String pattern, String requestPath) {
        String[] patternSegs = pattern.split("/", -1);
        String[] requestSegs = requestPath.split("/", -1);

        for (int i = 0; i < patternSegs.length; i++) {
            String ps = patternSegs[i];
            if ("**".equals(ps)) return true;
            if (i >= requestSegs.length) return false;
            if (ps.startsWith("{") && ps.endsWith("}")) continue;
            if (!ps.equals(requestSegs[i])) return false;
        }

        return patternSegs.length == requestSegs.length;
    }

    /**
     * Matches a request path against a wildcard route pattern (/** suffix only).
     * Requires a "/" boundary after the prefix to prevent false matches.
     *
     * Examples:
     *   matchesWildcard("/api/users/**", "/api/users/123")  → true
     *   matchesWildcard("/api/users/**", "/api/users")      → true
     *   matchesWildcard("/api/users/**", "/api/usersfoo")   → false
     */
    private boolean matchesWildcard(String routePath, String requestPath) {
        if (!routePath.endsWith("/**")) {
            return false;
        }
        String prefix = routePath.substring(0, routePath.length() - 3);
        return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
    }

    private int countLiteralSegments(String path) {
        int count = 0;
        for (String seg : path.split("/", -1)) {
            if (!seg.isEmpty() && !seg.startsWith("{") && !"**".equals(seg)) {
                count++;
            }
        }
        return count;
    }
}