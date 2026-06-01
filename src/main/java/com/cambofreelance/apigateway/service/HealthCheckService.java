package com.cambofreelance.apigateway.service;

import com.cambofreelance.apigateway.caches.ServiceInstanceCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.models.ServiceNode;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private static final String HEALTH_PATH   = "/actuator/health";
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final int CONCURRENCY      = 10;

    private static final String STATUS_UP    = "UP";
    private static final String STATUS_DOWN  = "DOWN";

    private final ServiceNodeRepository serviceNodeRepository;
    private final WebClient.Builder webClientBuilder;

    // ── Scheduled probe ───────────────────────────────────────────────────────

    /**
     * Probes every active service instance every 30 s (configurable).
     * Updates health_status in the DB, then refreshes the in-memory cache.
     * OUT_OF_SERVICE instances are skipped — they are managed manually.
     */
    @Scheduled(fixedDelayString = "${health-check.interval-ms:30000}",
               initialDelayString = "${health-check.initial-delay-ms:15000}")
    public void runScheduledChecks() {
        log.debug("Running scheduled health checks ...");
        probeAll().subscribe(
            count -> log.info("Health check cycle done: {}/{} instance(s) UP", count[0], count[1]),
            err   -> log.error("Health check cycle failed: {}", err.getMessage())
        );
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Triggers an immediate full health-check cycle and returns the refreshed list.
     * Used by the admin endpoint for on-demand checks.
     */
    public Mono<List<ServiceNode>> checkNow() {
        return serviceNodeRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .filter(node -> !"OUT_OF_SERVICE".equalsIgnoreCase(node.getHealthStatus()))
            .flatMap(this::probeAndPersist, CONCURRENCY)
            .collectList()
            .doOnSuccess(nodes -> {
                ServiceInstanceCache.init(nodes);
                long up = nodes.stream().filter(n -> STATUS_UP.equalsIgnoreCase(n.getHealthStatus())).count();
                log.info("On-demand health check: {}/{} instance(s) UP", up, nodes.size());
            });
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Mono<long[]> probeAll() {
        return serviceNodeRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .filter(node -> !"OUT_OF_SERVICE".equalsIgnoreCase(node.getHealthStatus()))
            .flatMap(this::probeAndPersist, CONCURRENCY)
            .collectList()
            .map(nodes -> {
                ServiceInstanceCache.init(nodes);
                long up = nodes.stream().filter(n -> STATUS_UP.equalsIgnoreCase(n.getHealthStatus())).count();
                return new long[]{up, nodes.size()};
            });
    }

    private Mono<ServiceNode> probeAndPersist(ServiceNode node) {
        String url = buildHealthUrl(node);
        return probe(url)
            .flatMap(newStatus -> {
                if (newStatus.equals(node.getHealthStatus())) {
                    // no change — skip DB write, just update the in-memory timestamp
                    node.setLastHealthCheck(LocalDateTime.now());
                    return Mono.just(node);
                }
                LocalDateTime now = LocalDateTime.now();
                return serviceNodeRepository
                    .updateHealthStatus(node.getId(), newStatus, now, now)
                    .doOnSuccess(r -> log.info("Instance {} ({}:{}) → {}",
                        node.getServiceId(), node.getHost(), node.getPort(), newStatus))
                    .thenReturn(node)
                    .doOnNext(n -> {
                        n.setHealthStatus(newStatus);
                        n.setLastHealthCheck(now);
                    });
            });
    }

    /**
     * Sends a GET to the instance health endpoint.
     * Returns UP on any 2xx response, DOWN on any error or non-2xx.
     */
    private Mono<String> probe(String url) {
        return webClientBuilder.build()
            .get()
            .uri(url)
            .exchangeToMono(response ->
                Mono.just(response.statusCode().is2xxSuccessful() ? STATUS_UP : STATUS_DOWN))
            .timeout(PROBE_TIMEOUT)
            .onErrorReturn(STATUS_DOWN);
    }

    private String buildHealthUrl(ServiceNode node) {
        return (node.isSecure() ? "https" : "http")
            + "://" + node.getHost() + ":" + node.getPort() + HEALTH_PATH;
    }

    // ── Cache reload helper (called by controller after manual status change) ─

    public Mono<Void> refreshCache() {
        return serviceNodeRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .doOnSuccess(ServiceInstanceCache::init)
            .then();
    }
}