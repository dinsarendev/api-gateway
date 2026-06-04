package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.service.impl.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/api/management/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final MetricsService metricsService;
    private final AdminAuthHelper adminAuth;

    /**
     * Gateway-wide metrics: RPS, throughput, success/error rate, P95/P99 latency, timeline.
     * @param window  look-back window in minutes (default 60, max 1440)
     */
    @GetMapping("/gateway")
    public Mono<ResponseEntity<Map<String, Object>>> gatewayMetrics(
            @RequestParam(defaultValue = "60") int window,
            ServerWebExchange exchange) {

        int w = Math.min(Math.max(window, 1), 1440);
        return adminAuth.require(exchange, Permissions.MONITORING_READ)
            .then(Mono.fromCallable(() -> metricsService.getGatewayMetrics(w))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok));
    }

    /**
     * Per-route API metrics: top APIs, slow APIs, failed APIs.
     * @param window  look-back window in hours (default 1, max 24)
     * @param limit   max rows per category (default 10)
     */
    @GetMapping("/apis")
    public Mono<ResponseEntity<Map<String, Object>>> apiMetrics(
            @RequestParam(defaultValue = "1") int window,
            @RequestParam(defaultValue = "10") int limit,
            ServerWebExchange exchange) {

        int w = Math.min(Math.max(window, 1), 24);
        int l = Math.min(Math.max(limit, 1), 50);
        return adminAuth.require(exchange, Permissions.MONITORING_READ)
            .then(Mono.fromCallable(() -> metricsService.getApiMetrics(w, l))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok));
    }

    /**
     * Consumer metrics: top consumers, abuse detection flags.
     * @param window  look-back window in hours (default 1, max 24)
     * @param limit   max consumers returned (default 10)
     */
    @GetMapping("/consumers")
    public Mono<ResponseEntity<Map<String, Object>>> consumerMetrics(
            @RequestParam(defaultValue = "1") int window,
            @RequestParam(defaultValue = "10") int limit,
            ServerWebExchange exchange) {

        int w = Math.min(Math.max(window, 1), 24);
        int l = Math.min(Math.max(limit, 1), 50);
        return adminAuth.require(exchange, Permissions.MONITORING_READ)
            .then(Mono.fromCallable(() -> metricsService.getConsumerMetrics(w, l))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok));
    }
}
