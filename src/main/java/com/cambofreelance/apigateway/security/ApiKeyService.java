package com.cambofreelance.apigateway.security;

import com.cambofreelance.apigateway.models.ApiKey;
import com.cambofreelance.apigateway.repositories.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String CACHE_PREFIX = "apikey:valid:";
    private static final Duration CACHE_TTL  = Duration.ofMinutes(5);
    private static final String KEY_PREFIX_GW = "gw_";
    private static final int KEY_PREFIX_LEN  = 12;

    private final ApiKeyRepository apiKeyRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Validates X-Api-Key header value. Returns principal on success, empty on failure. */
    public Mono<SecurityPrincipal> validate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX_GW) || rawKey.length() < KEY_PREFIX_LEN) {
            return Mono.empty();
        }

        String cacheKey = CACHE_PREFIX + sha256(rawKey);

        // Check Redis cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (Boolean.FALSE.equals(cached)) return Mono.empty();
        if (cached instanceof String cachedRoles) {
            String[] parts = cachedRoles.split("\\|", 3);
            return Mono.just(SecurityPrincipal.of(
                parts[0],
                splitComma(parts.length > 1 ? parts[1] : ""),
                splitComma(parts.length > 2 ? parts[2] : ""),
                "API_KEY"
            ));
        }

        String prefix = rawKey.substring(0, KEY_PREFIX_LEN);

        return apiKeyRepository.findActiveByPrefix(prefix)
            .publishOn(Schedulers.boundedElastic())
            .filter(k -> isKeyValid(rawKey, k))
            .next()
            .doOnSuccess(k -> {
                if (k == null) {
                    redisTemplate.opsForValue().set(cacheKey, Boolean.FALSE, CACHE_TTL);
                    return;
                }
                String val = (k.getClientId() != null ? k.getClientId() : "") + "|"
                    + (k.getRoles() != null ? k.getRoles() : "") + "|"
                    + (k.getPermissions() != null ? k.getPermissions() : "");
                redisTemplate.opsForValue().set(cacheKey, val, CACHE_TTL);
                apiKeyRepository.touchLastUsed(k.getId()).subscribe();
            })
            .map(k -> SecurityPrincipal.of(
                k.getClientId(),
                splitComma(k.getRoles()),
                splitComma(k.getPermissions()),
                "API_KEY"
            ));
    }

    /** Generates a new raw key, hashes it, and persists only the hash. Returns full key once. */
    public Mono<String> createKey(String name, String clientId, String roles, String permissions,
                                  LocalDateTime expiresAt) {
        String rawKey = KEY_PREFIX_GW + UUID.randomUUID().toString().replace("-", "");
        String prefix = rawKey.substring(0, KEY_PREFIX_LEN);
        String hash   = BCrypt.hashpw(rawKey, BCrypt.gensalt(12));

        ApiKey entity = ApiKey.builder()
            .name(name)
            .keyPrefix(prefix)
            .keyHash(hash)
            .clientId(clientId)
            .roles(roles)
            .permissions(permissions)
            .expiresAt(expiresAt)
            .build();

        return apiKeyRepository.save(entity).thenReturn(rawKey);
    }

    private boolean isKeyValid(String rawKey, ApiKey key) {
        if (!"ACT".equals(key.getStatus())) return false;
        if (key.getExpiresAt() != null && LocalDateTime.now().isAfter(key.getExpiresAt())) return false;
        return BCrypt.checkpw(rawKey, key.getKeyHash());
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (Exception e) {
            return input.hashCode() + "";
        }
    }

    private List<String> splitComma(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}