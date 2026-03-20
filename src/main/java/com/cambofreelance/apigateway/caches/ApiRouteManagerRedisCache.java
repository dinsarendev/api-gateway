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
                String redisKey = buildKey(route.getPath(), route.getMethod());
                hashOperations.put(KEY, redisKey, route);
            }

            log.info("API Route cache initialized: {} records", routes.size());

        } catch (Exception e) {
            log.error("Init cache error", e);
        }
    }

    // ================= ADD / UPDATE =================
    public void put(ApiRouteDto route) {
        try {
            String redisKey = buildKey(route.getPath(), route.getMethod());
            hashOperations.put(KEY, redisKey, route);
        } catch (Exception e) {
            log.error("Put cache error", e);
        }
    }

    public void evict(ApiRouteDto route) {
        try {
            String redisKey = buildKey(route.getPath(), route.getMethod());
            hashOperations.delete(KEY, redisKey);
        } catch (Exception e) {
            log.error("Evict cache error", e);
        }
    }

    // ================= GET =================
    public ApiRouteDto get(String path, String method) {
        try {
            // 1. Exact match (O(1))
            String key = buildKey(path, method);
            ApiRouteDto exact = (ApiRouteDto) hashOperations.get(KEY, key);

            if (exact != null) {
                return exact;
            }

            // 2. Wildcard match (fallback)
            Map<String, ApiRouteDto> entries = hashOperations.entries(KEY);

            return entries.values().stream()
                .filter(dto -> dto.getMethod().equalsIgnoreCase(method))
                .filter(dto -> dto.getPath().endsWith("/**"))
                .filter(dto -> path.startsWith(dto.getPath().replace("/**", "")))
                .findFirst()
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
}