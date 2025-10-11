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
  private RedisTemplate<String, Object> apiRouteDtoRedisTemplate;

  @PostConstruct
  private void init() {
    try {
      KEY = keyValue;
      hashOperations = apiRouteDtoRedisTemplate.opsForHash();
    } catch (RedisConnectionFailureException e) {
      log.error("Redis connection failed during initialization: {}", e.getMessage(), e);
    }
  }

  public void initCache(List<ApiRouteDto> routes) {
    try {
      apiRouteDtoRedisTemplate.delete(KEY);
      for (ApiRouteDto route : routes) {
        hashOperations.put(KEY, route.getPath(), route);
      }
    } catch (RedisConnectionFailureException e) {
      log.error("Failed to initialize Redis cache: {}", e.getMessage(), e);
    }
  }

  public void addCache(ApiRouteDto route) {
    try {
      hashOperations.put(KEY, route.getPath(), route);
    } catch (RedisConnectionFailureException e) {
      log.error("Failed to add route to Redis cache: {}", e.getMessage(), e);
    }
  }

  public void reloadCache(ApiRouteDto route) {
    try {
      hashOperations.put(KEY, route.getPath(), route);
    } catch (RedisConnectionFailureException e) {
      log.error("Failed to reload route in Redis cache: {}", e.getMessage(), e);
    }
  }

  public ApiRouteDto getPath(String path) {
    try {
      return hashOperations.get(KEY, path);
    } catch (RedisConnectionFailureException e) {
      log.error("Failed to retrieve path from Redis cache: {}", e.getMessage(), e);
      return null;
    }
  }

  public ApiRouteDto getPathAndMethod(String path, String method) {
    try {
      Map<String, ApiRouteDto> entries = hashOperations.entries(KEY);

      // Get Exact match first
      ApiRouteDto exactMatch = entries.values().stream()
              .filter(dto -> dto.getPath().equals(path) && dto.getMethod().equalsIgnoreCase(method))
              .findFirst()
              .orElse(null);

      if (exactMatch != null) {
        return exactMatch;
      }

      // Only get wildcard after not found exact match
      return entries.values().stream()
              .filter(dto -> dto.getPath().endsWith("/**"))
              .filter(dto -> path.startsWith(dto.getPath().substring(0, dto.getPath().length() - 3)))
              .filter(dto -> dto.getMethod().equalsIgnoreCase(method))
              .findFirst()
              .orElse(null);

    } catch (RedisConnectionFailureException e) {
      log.error("Failed to retrieve path/method from Redis cache: {}", e.getMessage(), e);
      return null;
    }
  }

}
