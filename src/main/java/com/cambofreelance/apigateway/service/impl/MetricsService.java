package com.cambofreelance.apigateway.service.impl;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsService {

    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${monitoring.abuse.threshold-per-minute:100}")
    private int abuseThreshold;

    // ── Gateway metrics ────────────────────────────────────────────────────────

    public Map<String, Object> getGatewayMetrics(int windowMinutes) {
        LocalDateTime now = LocalDateTime.now();

        List<String> reqKeys = new ArrayList<>();
        List<String> okKeys  = new ArrayList<>();
        List<String> errKeys = new ArrayList<>();
        List<String> latKeys = new ArrayList<>();

        for (int i = windowMinutes - 1; i >= 0; i--) {
            String mm = MetricsCollector.minuteBucket(now.minusMinutes(i));
            reqKeys.add("gw:req:" + mm);
            okKeys .add("gw:ok:"  + mm);
            errKeys.add("gw:err:" + mm);
            latKeys.add("gw:lat:" + mm);
        }

        List<String> reqVals = mget(reqKeys);
        List<String> okVals  = mget(okKeys);
        List<String> errVals = mget(errKeys);

        long total  = sum(reqVals);
        long ok     = sum(okVals);
        long errors = sum(errVals);

        double successRate = total > 0 ? Math.round(ok  * 1000.0 / total) / 10.0 : 100.0;
        double errorRate   = total > 0 ? Math.round(errors * 1000.0 / total) / 10.0 : 0.0;
        double rps         = windowMinutes > 0 ? Math.round(total * 10.0 / (windowMinutes * 60)) / 10.0 : 0.0;

        // Collect all latency samples across the window
        List<Long> allLatencies = new ArrayList<>();
        for (String lk : latKeys) {
            List<String> samples = stringRedisTemplate.opsForList().range(lk, 0, -1);
            if (samples != null) {
                for (String s : samples) {
                    try { allLatencies.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                }
            }
        }

        long p95 = percentile(allLatencies, 95);
        long p99 = percentile(allLatencies, 99);
        long avg = allLatencies.isEmpty() ? 0
            : (long) allLatencies.stream().mapToLong(Long::longValue).average().orElse(0);

        // Per-minute timeline for sparkline (last windowMinutes buckets)
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (int i = windowMinutes - 1; i >= 0; i--) {
            LocalDateTime bucket = now.minusMinutes(i);
            String mm = MetricsCollector.minuteBucket(bucket);
            long r = toLong(stringRedisTemplate.opsForValue().get("gw:req:" + mm));
            long e = toLong(stringRedisTemplate.opsForValue().get("gw:err:" + mm));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("minute", bucket.toString().substring(0, 16));
            point.put("requests", r);
            point.put("errors", e);
            timeline.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_requests",   total);
        result.put("rps",              rps);
        result.put("success_count",    ok);
        result.put("error_count",      errors);
        result.put("success_rate",     successRate);
        result.put("error_rate",       errorRate);
        result.put("avg_latency_ms",   avg);
        result.put("p95_latency_ms",   p95);
        result.put("p99_latency_ms",   p99);
        result.put("window_minutes",   windowMinutes);
        result.put("timeline",         timeline);
        return result;
    }

    // ── API metrics ────────────────────────────────────────────────────────────

    public Map<String, Object> getApiMetrics(int windowHours, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<String> hours = hoursBack(now, windowHours);

        // Aggregate top-api and fail-api sorted sets across hours
        Map<String, Long> reqCounts  = new HashMap<>();
        Map<String, Long> errCounts  = new HashMap<>();
        Map<String, Double> latSums  = new HashMap<>();

        for (String hh : hours) {
            // Top APIs by request count
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> topEntries =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores("gw:top:api:" + hh, 0, -1);
            if (topEntries != null) {
                for (var t : topEntries) {
                    if (t.getValue() == null || t.getScore() == null) continue;
                    reqCounts.merge(t.getValue(), t.getScore().longValue(), Long::sum);
                }
            }

            // Failed APIs
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> failEntries =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores("gw:fail:api:" + hh, 0, -1);
            if (failEntries != null) {
                for (var t : failEntries) {
                    if (t.getValue() == null || t.getScore() == null) continue;
                    errCounts.merge(t.getValue(), t.getScore().longValue(), Long::sum);
                }
            }

            // Latency sums per path|method
            Map<Object, Object> ls = stringRedisTemplate.opsForHash().entries("gw:slow:latsum:" + hh);
            for (var e : ls.entrySet()) {
                String k = e.getKey().toString();
                double v = toDouble(e.getValue().toString());
                latSums.merge(k, v, Double::sum);
            }
        }

        // Build per-API rows
        List<Map<String, Object>> apiRows = new ArrayList<>();
        for (var entry : reqCounts.entrySet()) {
            String key    = entry.getKey();
            long   req    = entry.getValue();
            long   err    = errCounts.getOrDefault(key, 0L);
            double latSum = latSums.getOrDefault(key, 0.0);
            long   avgLat = req > 0 ? (long) (latSum / req) : 0;
            double errRate = req > 0 ? Math.round(err * 1000.0 / req) / 10.0 : 0.0;

            String[] parts  = key.split("\\|", 2);
            String path   = parts.length > 0 ? parts[0] : key;
            String method = parts.length > 1 ? parts[1] : "";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",          key);
            row.put("path",         path);
            row.put("method",       method);
            row.put("requests",     req);
            row.put("errors",       err);
            row.put("error_rate",   errRate);
            row.put("avg_latency_ms", avgLat);
            apiRows.add(row);
        }

        // Top APIs: most requests
        List<Map<String, Object>> topApis = apiRows.stream()
            .sorted(Comparator.<Map<String, Object>, Long>comparing(r -> (Long) r.get("requests")).reversed())
            .limit(limit)
            .collect(Collectors.toList());

        // Slow APIs: highest avg latency (min 10 requests)
        List<Map<String, Object>> slowApis = apiRows.stream()
            .filter(r -> (Long) r.get("requests") >= 10)
            .sorted(Comparator.<Map<String, Object>, Long>comparing(r -> (Long) r.get("avg_latency_ms")).reversed())
            .limit(limit)
            .collect(Collectors.toList());

        // Failed APIs: most errors
        List<Map<String, Object>> failedApis = apiRows.stream()
            .filter(r -> (Long) r.get("errors") > 0)
            .sorted(Comparator.<Map<String, Object>, Long>comparing(r -> (Long) r.get("errors")).reversed())
            .limit(limit)
            .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("top_apis",    topApis);
        result.put("slow_apis",   slowApis);
        result.put("failed_apis", failedApis);
        result.put("window_hours", windowHours);
        return result;
    }

    // ── Consumer metrics ───────────────────────────────────────────────────────

    public Map<String, Object> getConsumerMetrics(int windowHours, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<String> hours = hoursBack(now, windowHours);

        // Aggregate top consumers across hours
        Map<String, Long> consumerReqs = new HashMap<>();
        for (String hh : hours) {
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> entries =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores("gw:top:con:" + hh, 0, -1);
            if (entries != null) {
                for (var t : entries) {
                    if (t.getValue() == null || t.getScore() == null) continue;
                    consumerReqs.merge(t.getValue(), t.getScore().longValue(), Long::sum);
                }
            }
        }

        List<Map<String, Object>> topConsumers = consumerReqs.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("consumer_id", e.getKey());
                row.put("requests",    e.getValue());
                return row;
            })
            .collect(Collectors.toList());

        // Abuse detection: check current-minute counters for top consumers
        String mm = MetricsCollector.minuteBucket(now);
        List<Map<String, Object>> abuseFlags = new ArrayList<>();
        for (var entry : consumerReqs.entrySet()) {
            String consumerId = entry.getKey();
            long perMin = toLong(stringRedisTemplate.opsForValue().get("gw:abuse:" + consumerId + ":" + mm));
            if (perMin >= abuseThreshold) {
                Map<String, Object> flag = new LinkedHashMap<>();
                flag.put("consumer_id",        consumerId);
                flag.put("requests_per_minute", perMin);
                flag.put("threshold",           abuseThreshold);
                flag.put("flagged",             true);
                abuseFlags.add(flag);
            }
        }
        abuseFlags.sort(Comparator.<Map<String, Object>, Long>comparing(
            r -> (Long) r.get("requests_per_minute")).reversed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("top_consumers",  topConsumers);
        result.put("abuse_flags",    abuseFlags);
        result.put("abuse_threshold", abuseThreshold);
        result.put("window_hours",   windowHours);
        return result;
    }

    // ── System / JVM metrics ───────────────────────────────────────────────────

    public Map<String, Object> getSystemMetrics() {
        double heapUsed  = sumGauges("jvm.memory.used", "area", "heap");
        double heapMax   = sumGauges("jvm.memory.max",  "area", "heap");
        double nonHeapUsed = sumGauges("jvm.memory.used", "area", "nonheap");

        double cpuSystem  = readGauge("system.cpu.usage");
        double cpuProcess = readGauge("process.cpu.usage");
        double uptime     = readGauge("process.uptime");

        double threadsLive   = readGauge("jvm.threads.live");
        double threadsDaemon = readGauge("jvm.threads.daemon");
        double threadsPeak   = readGauge("jvm.threads.peak");

        // Aggregate gateway.request.duration timer across all tags
        long timerCount = 0;
        double timerTotalMs = 0.0;
        for (Timer t : meterRegistry.find("gateway.request.duration").timers()) {
            timerCount   += t.count();
            timerTotalMs += t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        long timerAvgMs = timerCount > 0 ? (long) (timerTotalMs / timerCount) : 0;

        // Aggregate gateway.requests.total counter across all tags
        long totalRequests = 0;
        for (var c : meterRegistry.find("gateway.requests.total").counters()) {
            totalRequests += (long) c.count();
        }
        long totalErrors = 0;
        for (var c : meterRegistry.find("gateway.errors.total").counters()) {
            totalErrors += (long) c.count();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heap_used_mb",    heapUsed   >= 0 ? Math.round(heapUsed   / (1024 * 1024)) : -1);
        jvm.put("heap_max_mb",     heapMax    >= 0 ? Math.round(heapMax    / (1024 * 1024)) : -1);
        jvm.put("heap_usage_pct",  heapMax    >  0 ? Math.round(heapUsed / heapMax * 1000.0) / 10.0 : -1.0);
        jvm.put("non_heap_used_mb", nonHeapUsed >= 0 ? Math.round(nonHeapUsed / (1024 * 1024)) : -1);
        jvm.put("threads_live",    (long) threadsLive);
        jvm.put("threads_daemon",  (long) threadsDaemon);
        jvm.put("threads_peak",    (long) threadsPeak);
        result.put("jvm", jvm);

        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("system_pct",  cpuSystem  >= 0 ? Math.round(cpuSystem  * 1000.0) / 10.0 : -1.0);
        cpu.put("process_pct", cpuProcess >= 0 ? Math.round(cpuProcess * 1000.0) / 10.0 : -1.0);
        result.put("cpu", cpu);

        Map<String, Object> gateway = new LinkedHashMap<>();
        gateway.put("total_requests_lifetime", totalRequests);
        gateway.put("total_errors_lifetime",   totalErrors);
        gateway.put("avg_latency_ms_lifetime", timerAvgMs);
        result.put("gateway", gateway);

        result.put("uptime_seconds", uptime >= 0 ? (long) uptime : -1);
        result.put("timestamp",      System.currentTimeMillis());
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<String> mget(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        List<String> vals = stringRedisTemplate.opsForValue().multiGet(keys);
        return vals != null ? vals : Collections.nCopies(keys.size(), null);
    }

    private long sum(List<String> vals) {
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

    private long percentile(List<Long> samples, int p) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private List<String> hoursBack(LocalDateTime now, int hours) {
        List<String> list = new ArrayList<>();
        for (int i = hours - 1; i >= 0; i--) {
            list.add(MetricsCollector.hourBucket(now.minusHours(i)));
        }
        return list;
    }

    private double readGauge(String name) {
        try {
            Gauge g = meterRegistry.find(name).gauge();
            if (g == null) return -1.0;
            double v = g.value();
            return Double.isNaN(v) ? -1.0 : v;
        } catch (Exception e) {
            return -1.0;
        }
    }

    private double sumGauges(String name, String tagKey, String tagValue) {
        try {
            return meterRegistry.find(name).tag(tagKey, tagValue).gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .filter(v -> !Double.isNaN(v) && v >= 0)
                .sum();
        } catch (Exception e) {
            return -1.0;
        }
    }
}
