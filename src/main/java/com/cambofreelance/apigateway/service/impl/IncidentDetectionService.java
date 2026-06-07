package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.caches.ApiRouteManagerCache;
import com.cambofreelance.apigateway.caches.ServiceInstanceCache;
import com.cambofreelance.apigateway.models.Incident;
import com.cambofreelance.apigateway.models.ServiceNode;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every 60 s, checks Redis metrics and DB health state, and creates or
 * auto-resolves incidents when conditions breach or recover.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentDetectionService {

    private final IncidentService       incidentService;
    private final ServiceNodeRepository serviceNodeRepository;
    private final StringRedisTemplate   stringRedisTemplate;
    private final MeterRegistry         meterRegistry;

    @Value("${incident.detection.enabled:true}")
    private boolean detectionEnabled;

    @Value("${incident.detection.window-minutes:5}")
    private int windowMinutes;

    // Global thresholds
    @Value("${incident.detection.error-rate-threshold:0.10}")
    private double errorRateThreshold;          // 5xx spike

    @Value("${incident.detection.auth-failure-threshold:0.15}")
    private double authFailureThreshold;        // 401 spike

    @Value("${incident.detection.timeout-rate-threshold:0.05}")
    private double timeoutRateThreshold;        // 504 spike

    // Per-route thresholds
    @Value("${incident.detection.p95-latency-threshold-ms:2000}")
    private long latencyThresholdMs;

    @Value("${incident.detection.api-down-error-rate:0.95}")
    private double apiDownErrorRate;

    @Value("${incident.detection.api-down-min-requests:10}")
    private long apiDownMinRequests;

    // Infrastructure thresholds
    @Value("${incident.detection.cpu-threshold:0.80}")
    private double cpuThreshold;          // system CPU usage (0.0–1.0)

    @Value("${incident.detection.memory-threshold:0.85}")
    private double memoryThreshold;       // JVM heap usage (0.0–1.0)

    // ── Scheduled entry point ──────────────────────────────────────────────────

    @Scheduled(fixedDelayString  = "${incident.detection.interval-ms:60000}",
               initialDelayString = "${incident.detection.initial-delay-ms:60000}")
    public void detect() {
        if (!detectionEnabled) return;
        try {
            detect5xxSpike();
            detectAuthFailureSpike();
            detectTimeoutSpike();
            detectServiceDown();
            detectApiDown();
            detectSlowApis();
            detectHighCpu();
            detectHighMemory();
        } catch (Exception e) {
            log.error("Incident detection cycle failed: {}", e.getMessage(), e);
        }
    }

    // ── 1. Global 5xx error spike ──────────────────────────────────────────────

    private void detect5xxSpike() {
        long req  = sumMinutes("gw:req:", windowMinutes);
        long errs = sumMinutes("gw:5xx:", windowMinutes);
        if (req < 10) return; // not enough traffic

        double rate = (double) errs / req;
        String triggerKey = "global_5xx";

        if (rate > errorRateThreshold) {
            String pct = String.format("%.1f%%", rate * 100);
            create(Incident.builder()
                .triggerKey(triggerKey)
                .title("5xx Error Spike Detected")
                .description(String.format(
                    "Server error rate is %.1f%% over the last %d minutes (threshold: %.0f%%).",
                    rate * 100, windowMinutes, errorRateThreshold * 100))
                .type(Incident.TYPE_ERROR)
                .severity(rate > 0.25 ? Incident.SEV_CRITICAL : Incident.SEV_HIGH)
                .triggerValue(pct)
                .triggerThreshold(String.format("%.0f%%", errorRateThreshold * 100))
                .build());
        } else {
            autoResolve(triggerKey);
        }
    }

    // ── 2. Authentication failure spike ───────────────────────────────────────

    private void detectAuthFailureSpike() {
        long req  = sumMinutes("gw:req:", windowMinutes);
        long auth = sumMinutes("gw:401:", windowMinutes);
        if (req < 10) return;

        double rate = (double) auth / req;
        String triggerKey = "global_auth_failure";

        if (rate > authFailureThreshold) {
            String pct = String.format("%.1f%%", rate * 100);
            create(Incident.builder()
                .triggerKey(triggerKey)
                .title("Authentication Failure Spike")
                .description(String.format(
                    "Authentication failure rate is %.1f%% over the last %d minutes (threshold: %.0f%%).",
                    rate * 100, windowMinutes, authFailureThreshold * 100))
                .type(Incident.TYPE_ERROR)
                .severity(Incident.SEV_HIGH)
                .triggerValue(pct)
                .triggerThreshold(String.format("%.0f%%", authFailureThreshold * 100))
                .build());
        } else {
            autoResolve(triggerKey);
        }
    }

    // ── 3. Timeout / 504 spike ─────────────────────────────────────────────────

    private void detectTimeoutSpike() {
        long req     = sumMinutes("gw:req:", windowMinutes);
        long timeout = sumMinutes("gw:504:", windowMinutes);
        if (req < 10) return;

        double rate = (double) timeout / req;
        String triggerKey = "global_timeout";

        if (rate > timeoutRateThreshold) {
            String pct = String.format("%.1f%%", rate * 100);
            create(Incident.builder()
                .triggerKey(triggerKey)
                .title("Timeout Rate Spike")
                .description(String.format(
                    "Gateway timeout (504) rate is %.1f%% over the last %d minutes (threshold: %.0f%%).",
                    rate * 100, windowMinutes, timeoutRateThreshold * 100))
                .type(Incident.TYPE_PERFORMANCE)
                .severity(Incident.SEV_HIGH)
                .triggerValue(pct)
                .triggerThreshold(String.format("%.0f%%", timeoutRateThreshold * 100))
                .build());
        } else {
            autoResolve(triggerKey);
        }
    }

    // ── 4. Service instance DOWN ───────────────────────────────────────────────

    private void detectServiceDown() {
        List<ServiceNode> downNodes;
        try {
            downNodes = serviceNodeRepository
                .findAllByHealthStatusAndStatus("DOWN", "ACT")
                .collectList().block();
        } catch (Exception e) {
            log.warn("detectServiceDown: DB query failed: {}", e.getMessage());
            return;
        }
        if (downNodes == null) return;

        // Collect all service IDs that currently have UP instances
        List<String> allServiceIds = ServiceInstanceCache.getServiceIds();

        // Create incident for each DOWN node; auto-resolve when it comes back UP
        for (ServiceNode node : downNodes) {
            String triggerKey = "service_down:" + node.getServiceId().toUpperCase()
                + ":" + node.getHost() + ":" + node.getPort();
            create(Incident.builder()
                .triggerKey(triggerKey)
                .title("Service Instance Unavailable: " + node.getServiceId().toUpperCase())
                .description(String.format(
                    "Instance %s:%d for service %s is reporting DOWN.",
                    node.getHost(), node.getPort(), node.getServiceId().toUpperCase()))
                .type(Incident.TYPE_AVAILABILITY)
                .severity(Incident.SEV_CRITICAL)
                .affectedService(node.getServiceId().toUpperCase())
                .triggerValue("DOWN")
                .triggerThreshold("UP")
                .build());
        }

        // Auto-resolve: service IDs that have all instances UP
        for (String serviceId : allServiceIds) {
            boolean anyDown = downNodes.stream()
                .anyMatch(n -> serviceId.equalsIgnoreCase(n.getServiceId()));
            if (!anyDown) {
                // Find and resolve any open service_down incidents for this service
                try {
                    List<ServiceNode> nodes = ServiceInstanceCache.getInstances(serviceId);
                    for (ServiceNode n : nodes) {
                        String tk = "service_down:" + serviceId.toUpperCase()
                            + ":" + n.getHost() + ":" + n.getPort();
                        autoResolve(tk);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ── 5. API down (per-route high error rate) ────────────────────────────────

    private void detectApiDown() {
        String hh = MetricsCollector.hourBucket(LocalDateTime.now());
        List<String> serviceIds = ServiceInstanceCache.getServiceIds();

        for (String serviceId : serviceIds) {
            List<com.cambofreelance.apigateway.dto.ApiRouteDto> routes;
            try {
                routes = ApiRouteManagerCache.getByGroupCode(serviceId);
            } catch (Exception e) {
                continue;
            }
            for (var route : routes) {
                String rid     = String.valueOf(route.getId());
                long   req     = toLong(stringRedisTemplate.opsForValue().get("gw:rt:" + rid + ":req:" + hh));
                long   err     = toLong(stringRedisTemplate.opsForValue().get("gw:rt:" + rid + ":err:" + hh));
                String triggerKey = "api_down:" + rid;

                if (req >= apiDownMinRequests && (double) err / req >= apiDownErrorRate) {
                    String pct = String.format("%.1f%%", (double) err / req * 100);
                    create(Incident.builder()
                        .triggerKey(triggerKey)
                        .title("API Down: " + route.getPath())
                        .description(String.format(
                            "Route %s %s has %.1f%% error rate this hour (%d/%d requests failed).",
                            route.getMethod(), route.getPath(),
                            (double) err / req * 100, err, req))
                        .type(Incident.TYPE_AVAILABILITY)
                        .severity(Incident.SEV_CRITICAL)
                        .affectedService(route.getGroupCode())
                        .affectedRoute(route.getPath())
                        .triggerValue(pct)
                        .triggerThreshold(String.format("%.0f%%", apiDownErrorRate * 100))
                        .build());
                } else if (req > 0) {
                    autoResolve(triggerKey);
                }
            }
        }
    }

    // ── 6. Slow API (high avg latency per route this hour) ────────────────────

    private void detectSlowApis() {
        String hh = MetricsCollector.hourBucket(LocalDateTime.now());
        List<String> serviceIds = ServiceInstanceCache.getServiceIds();

        for (String serviceId : serviceIds) {
            List<com.cambofreelance.apigateway.dto.ApiRouteDto> routes;
            try {
                routes = ApiRouteManagerCache.getByGroupCode(serviceId);
            } catch (Exception e) {
                continue;
            }
            for (var route : routes) {
                String rid    = String.valueOf(route.getId());
                long   req    = toLong(stringRedisTemplate.opsForValue().get("gw:rt:" + rid + ":req:" + hh));
                double latsum = toDouble(stringRedisTemplate.opsForValue().get("gw:rt:" + rid + ":latsum:" + hh));
                if (req < 10) continue;

                long avgLat    = (long) (latsum / req);
                String triggerKey = "slow_api:" + rid;

                if (avgLat > latencyThresholdMs) {
                    create(Incident.builder()
                        .triggerKey(triggerKey)
                        .title("High Latency: " + route.getPath())
                        .description(String.format(
                            "Route %s %s avg latency is %dms this hour (threshold: %dms).",
                            route.getMethod(), route.getPath(), avgLat, latencyThresholdMs))
                        .type(Incident.TYPE_PERFORMANCE)
                        .severity(avgLat > latencyThresholdMs * 2 ? Incident.SEV_CRITICAL : Incident.SEV_HIGH)
                        .affectedService(route.getGroupCode())
                        .affectedRoute(route.getPath())
                        .triggerValue(avgLat + "ms")
                        .triggerThreshold(latencyThresholdMs + "ms")
                        .build());
                } else {
                    autoResolve(triggerKey);
                }
            }
        }
    }

    // ── 7. CPU > threshold ─────────────────────────────────────────────────────

    private void detectHighCpu() {
        double cpu = readGauge("system.cpu.usage");
        if (cpu < 0) cpu = readGauge("process.cpu.usage"); // fallback to process CPU
        if (cpu < 0) return; // metric unavailable on this JVM

        String triggerKey = "infra_cpu_high";
        if (cpu > cpuThreshold) {
            String pct = String.format("%.1f%%", cpu * 100);
            create(Incident.builder()
                .triggerKey(triggerKey)
                .title(String.format("High CPU Usage: %.1f%%", cpu * 100))
                .description(String.format(
                    "System CPU utilization reached %.1f%% (threshold: %.0f%%).",
                    cpu * 100, cpuThreshold * 100))
                .type(Incident.TYPE_INFRASTRUCTURE)
                .severity(cpu > 0.95 ? Incident.SEV_CRITICAL : Incident.SEV_HIGH)
                .triggerValue(pct)
                .triggerThreshold(String.format("%.0f%%", cpuThreshold * 100))
                .build());
        } else {
            autoResolve(triggerKey);
        }
    }

    // ── 8. JVM heap memory > threshold ────────────────────────────────────────

    private void detectHighMemory() {
        double used = sumGauges("jvm.memory.used", "area", "heap");
        double max  = sumGauges("jvm.memory.max",  "area", "heap");
        if (max <= 0) return; // metric unavailable

        double ratio = used / max;
        String triggerKey = "infra_memory_high";

        if (ratio > memoryThreshold) {
            String pct     = String.format("%.1f%%", ratio * 100);
            String detail  = String.format("%.0f MB / %.0f MB",
                used / (1024 * 1024), max / (1024 * 1024));
            create(Incident.builder()
                .triggerKey(triggerKey)
                .title(String.format("High Memory Usage: %.1f%%", ratio * 100))
                .description(String.format(
                    "JVM heap memory usage reached %.1f%% (%s). Threshold: %.0f%%.",
                    ratio * 100, detail, memoryThreshold * 100))
                .type(Incident.TYPE_INFRASTRUCTURE)
                .severity(ratio > 0.95 ? Incident.SEV_CRITICAL : Incident.SEV_HIGH)
                .triggerValue(pct)
                .triggerThreshold(String.format("%.0f%%", memoryThreshold * 100))
                .build());
        } else {
            autoResolve(triggerKey);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void create(Incident template) {
        incidentService.createAuto(template)
            .subscribe(
                i -> { if (i != null) log.warn("AUTO INCIDENT [{}]: {}", i.getSeverity(), i.getTitle()); },
                e -> log.error("Failed to create auto incident: {}", e.getMessage())
            );
    }

    private void autoResolve(String triggerKey) {
        incidentService.autoResolve(triggerKey)
            .filter(rows -> rows > 0)
            .subscribe(
                rows -> log.info("Auto-resolved incident trigger_key={}", triggerKey),
                e    -> log.error("Auto-resolve failed for {}: {}", triggerKey, e.getMessage())
            );
    }

    private long sumMinutes(String prefix, int minutes) {
        LocalDateTime now = LocalDateTime.now();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < minutes; i++) {
            keys.add(prefix + MetricsCollector.minuteBucket(now.minusMinutes(i)));
        }
        List<String> vals = stringRedisTemplate.opsForValue().multiGet(keys);
        if (vals == null) return 0;
        return vals.stream().mapToLong(v -> v == null ? 0 : toLong(v)).sum();
    }

    private long toLong(String v) {
        if (v == null) return 0;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private double toDouble(String v) {
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    /** Returns the value of a single named Gauge, or -1.0 if not registered. */
    private double readGauge(String name) {
        try {
            Gauge gauge = meterRegistry.find(name).gauge();
            if (gauge == null) return -1.0;
            double v = gauge.value();
            return Double.isNaN(v) ? -1.0 : v;
        } catch (Exception e) {
            return -1.0;
        }
    }

    /** Sums values of all Gauges matching name + tag pair (e.g. multiple heap regions). */
    private double sumGauges(String name, String tagKey, String tagValue) {
        try {
            return meterRegistry.find(name).tag(tagKey, tagValue).gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .filter(v -> !Double.isNaN(v) && v >= 0)
                .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
