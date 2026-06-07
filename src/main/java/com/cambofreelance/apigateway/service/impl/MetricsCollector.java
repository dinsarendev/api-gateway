package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.dto.ApiRouteDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsCollector {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final MeterRegistry meterRegistry;

    private static final DateTimeFormatter MIN_FMT  = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private static final Duration TTL_MIN  = Duration.ofHours(2);   // minute-bucket keys
    private static final Duration TTL_HOUR = Duration.ofHours(25);  // hour-bucket keys

    private static final int MAX_LAT_SAMPLES = 1000;

    // Called from RequestLoggingFilter.doFinally — must never throw
    public void record(ApiRouteDto route, int status, long latencyMs, String consumerId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String mm = now.format(MIN_FMT);
            String hh = now.format(HOUR_FMT);

            // ── Gateway-wide per-minute counters ───────────────────────────
            incr("gw:req:" + mm, TTL_MIN);
            if (status >= 200 && status < 400) {
                incr("gw:ok:" + mm, TTL_MIN);
            } else if (status >= 400) {
                incr("gw:err:" + mm, TTL_MIN);
            }
            // Fine-grained status counters for incident detection
            if (status >= 500)       incr("gw:5xx:" + mm, TTL_MIN);
            if (status == 401)       incr("gw:401:" + mm, TTL_MIN);
            if (status == 504)       incr("gw:504:" + mm, TTL_MIN);
            // Keep up to 1000 latency samples per minute for P95/P99
            lpush("gw:lat:" + mm, String.valueOf(latencyMs), TTL_MIN);

            // ── Per-route per-hour counters ────────────────────────────────
            if (route != null) {
                String rid    = String.valueOf(route.getId());
                String member = route.getPath() + "|" + route.getMethod();

                incr("gw:rt:" + rid + ":req:" + hh, TTL_HOUR);
                incrbyfloat("gw:rt:" + rid + ":latsum:" + hh, latencyMs, TTL_HOUR);
                if (status >= 400) {
                    incr("gw:rt:" + rid + ":err:" + hh, TTL_HOUR);
                }

                // Sorted sets for Top / Failed APIs
                zincrby("gw:top:api:" + hh, member, TTL_HOUR);
                if (status >= 400) {
                    zincrby("gw:fail:api:" + hh, member, TTL_HOUR);
                }
                // Hash of per-route total latency sums → avg latency for Slow APIs
                hincrbyfloat("gw:slow:latsum:" + hh, member, latencyMs, TTL_HOUR);
            }

            // ── Consumer per-hour + abuse per-minute ──────────────────────
            if (consumerId != null) {
                zincrby("gw:top:con:" + hh, consumerId, TTL_HOUR);
                incr("gw:abuse:" + consumerId + ":" + mm, TTL_MIN);
            }

            // ── Micrometer (Prometheus) ────────────────────────────────────
            String routeTag  = route != null ? route.getPath()   : "unknown";
            String methodTag = route != null ? route.getMethod()  : "UNKNOWN";
            String statusClass = statusClass(status);

            Counter.builder("gateway.requests.total")
                .tag("route", routeTag)
                .tag("method", methodTag)
                .tag("status_class", statusClass)
                .register(meterRegistry)
                .increment();

            Timer.builder("gateway.request.duration")
                .tag("route", routeTag)
                .tag("method", methodTag)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);

            if (status >= 400) {
                Counter.builder("gateway.errors.total")
                    .tag("route", routeTag)
                    .tag("method", methodTag)
                    .tag("status", String.valueOf(status))
                    .register(meterRegistry)
                    .increment();
            }

        } catch (Exception e) {
            log.debug("MetricsCollector.record error: {}", e.getMessage());
        }
    }

    private static String statusClass(int status) {
        if (status < 200) return "1xx";
        if (status < 300) return "2xx";
        if (status < 400) return "3xx";
        if (status < 500) return "4xx";
        return "5xx";
    }

    // ── Fire-and-forget helpers ────────────────────────────────────────────────

    private void incr(String key, Duration ttl) {
        reactiveRedisTemplate.opsForValue().increment(key)
            .flatMap(v -> reactiveRedisTemplate.expire(key, ttl))
            .subscribe(null, e -> log.trace("metrics incr {}: {}", key, e.getMessage()));
    }

    private void incrbyfloat(String key, long delta, Duration ttl) {
        reactiveRedisTemplate.opsForValue().increment(key, (double) delta)
            .flatMap(v -> reactiveRedisTemplate.expire(key, ttl))
            .subscribe(null, e -> log.trace("metrics incrbyfloat {}: {}", key, e.getMessage()));
    }

    private void lpush(String key, String value, Duration ttl) {
        reactiveRedisTemplate.opsForList().leftPush(key, value)
            .flatMap(size -> size > MAX_LAT_SAMPLES
                ? reactiveRedisTemplate.opsForList().trim(key, 0, MAX_LAT_SAMPLES - 1)
                : reactiveRedisTemplate.expire(key, ttl).thenReturn(Boolean.TRUE))
            .flatMap(v -> reactiveRedisTemplate.expire(key, ttl))
            .subscribe(null, e -> log.trace("metrics lpush {}: {}", key, e.getMessage()));
    }

    private void zincrby(String key, String member, Duration ttl) {
        reactiveRedisTemplate.opsForZSet().incrementScore(key, member, 1.0)
            .flatMap(v -> reactiveRedisTemplate.expire(key, ttl))
            .subscribe(null, e -> log.trace("metrics zincrby {}: {}", key, e.getMessage()));
    }

    private void hincrbyfloat(String key, String field, long delta, Duration ttl) {
        reactiveRedisTemplate.opsForHash().increment(key, field, (double) delta)
            .flatMap(v -> reactiveRedisTemplate.expire(key, ttl))
            .subscribe(null, e -> log.trace("metrics hincrbyfloat {}: {}", key, e.getMessage()));
    }

    // ── Bucket utilities (used by MetricsService) ──────────────────────────────

    public static String minuteBucket(LocalDateTime t) {
        return t.format(MIN_FMT);
    }

    public static String hourBucket(LocalDateTime t) {
        return t.format(HOUR_FMT);
    }
}
