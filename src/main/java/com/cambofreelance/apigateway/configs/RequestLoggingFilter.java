package com.cambofreelance.apigateway.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import com.cambofreelance.apigateway.caches.ApiRouteManagerCache;
import com.cambofreelance.apigateway.caches.ApiRouteManagerRedisCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.ErrorCode;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import com.cambofreelance.apigateway.exception.MessageResponse;
import com.cambofreelance.apigateway.exception.RouteNotFoundException;
import com.cambofreelance.apigateway.security.IpFilterService;
import com.cambofreelance.apigateway.security.SecurityPrincipal;
import com.cambofreelance.apigateway.security.SecurityService;
import com.cambofreelance.apigateway.service.impl.MetricsCollector;
import com.cambofreelance.apigateway.service.impl.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter {

    private final RateLimiterService rateLimiterService;
    private final ApiRouteManagerRedisCache apiRouteManagerRedisCache;
    private final SecurityService securityService;
    private final IpFilterService ipFilterService;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final MetricsCollector metricsCollector;

    public RequestLoggingFilter(RateLimiterService rateLimiterService,
                                ApiRouteManagerRedisCache apiRouteManagerRedisCache,
                                SecurityService securityService,
                                IpFilterService ipFilterService,
                                ObjectMapper objectMapper,
                                Tracer tracer,
                                MetricsCollector metricsCollector) {
        this.rateLimiterService        = rateLimiterService;
        this.apiRouteManagerRedisCache = apiRouteManagerRedisCache;
        this.securityService           = securityService;
        this.ipFilterService           = ipFilterService;
        this.objectMapper              = objectMapper;
        this.tracer                    = tracer;
        this.metricsCollector          = metricsCollector;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path   = request.getPath().toString();
        String method = Optional.of(request.getMethod()).map(Object::toString).orElse(Constants.UNKNOWN);

        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        long startNano       = System.nanoTime();
        String clientIp      = resolveClientIp(request);
        String correlationId = resolveCorrelationId(request);

        log.info("Inbound: correlationId={}, method={}, path={}, ip={}", correlationId, method, path, clientIp);
        exchange.getResponse().getHeaders().set(Constants.CORRELATION_ID, correlationId);

        return resolveRoute(path, method)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("API route not found")))
            .flatMap(routeDto -> {
                if (!isRouteAvailableNow(routeDto)) {
                    return Mono.error(new RouteNotFoundException("API route not found"));
                }
                return processRequest(exchange, chain, routeDto, startNano, clientIp, correlationId, request, method);
            })
            .onErrorResume(RouteNotFoundException.class, e ->
                errorResponse(exchange, HttpStatus.NOT_FOUND, ErrorCode.ERR_00404, "API route not found")
                    .doFinally(s -> metricsCollector.record(null, 404, elapsedMs(startNano), clientIp))
            );
    }

    // ── Route resolution: Redis (O(1) exact) → in-memory (path-var / wildcard) ──

    private Mono<ApiRouteDto> resolveRoute(String path, String method) {
        return apiRouteManagerRedisCache.get(path, method)
            .switchIfEmpty(Mono.defer(() -> {
                ApiRouteDto cached = ApiRouteManagerCache.get(path, method);
                return cached != null ? Mono.just(cached) : Mono.empty();
            }));
    }

    // ── Security / rate-limit chain ────────────────────────────────────────────

    private Mono<Void> processRequest(ServerWebExchange exchange, GatewayFilterChain chain,
                                      ApiRouteDto route, long startNano, String clientIp,
                                      String correlationId, ServerHttpRequest request, String method) {
        return ipFilterService.isAllowed(clientIp, route)
            .flatMap(allowed -> {
                if (Boolean.FALSE.equals(allowed)) {
                    log.warn("IP blocked: {} → {} {}", clientIp, method, route.getPath());
                    return errorResponse(exchange, HttpStatus.FORBIDDEN, ErrorCode.ERR_00403,
                        "Access denied from IP: " + clientIp);
                }

                if (Constants.YES.equalsIgnoreCase(route.getIsPublic())) {
                    ServerWebExchange out = withUpstreamHeaders(exchange, request, clientIp, correlationId, null, null);
                    return applyRateLimitOrContinue(out, chain, method, clientIp, route);
                }

                return securityService.authenticate(exchange, route)
                    .flatMap(principal ->
                        securityService.checkRoles(principal, route)
                            .then(securityService.checkPermissions(principal, route))
                            .thenReturn(principal)
                    )
                    .flatMap(principal -> {
                        ServerWebExchange out = withUpstreamHeaders(
                            exchange, request, clientIp, correlationId, principal.userId(), principal);
                        return applyRateLimitOrContinue(out, chain, method, clientIp, route);
                    })
                    .onErrorResume(SecurityService.UnauthorizedException.class, e ->
                        errorResponse(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.ERR_00401, e.getMessage()))
                    .onErrorResume(SecurityService.ForbiddenException.class, e ->
                        errorResponse(exchange, HttpStatus.FORBIDDEN, ErrorCode.ERR_00403, e.getMessage()))
                    .onErrorResume(SecurityException.class, e ->
                        errorResponse(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.ERR_00401, e.getMessage()));
            })
            .doFinally(s -> {
                long ms = elapsedMs(startNano);
                int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 200;
                metricsCollector.record(route, status, ms, clientIp);
            });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    private ServerWebExchange withUpstreamHeaders(ServerWebExchange exchange,
                                                   ServerHttpRequest request,
                                                   String clientIp,
                                                   String correlationId,
                                                   String userId,
                                                   SecurityPrincipal principal) {
        ServerHttpRequest.Builder builder = request.mutate()
            .header(Constants.IP, clientIp)
            .header(Constants.CORRELATION_ID, correlationId);

        if (StringUtils.isNotBlank(userId)) {
            builder.header(Constants.USER_ID, userId);
        }
        if (principal != null && !principal.roles().isEmpty()) {
            builder.header("X-User-Roles", String.join(",", principal.roles()));
        }
        if (principal != null && !principal.permissions().isEmpty()) {
            builder.header("X-User-Permissions", String.join(",", principal.permissions()));
        }

        var sslInfo = request.getSslInfo();
        if (sslInfo != null) {
            var certs = sslInfo.getPeerCertificates();
            if (certs != null && certs.length > 0) {
                try {
                    java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) certs[0];
                    builder.header("X-Client-Cert-CN", cert.getSubjectX500Principal().getName());
                } catch (Exception ignored) {}
            }
        }

        return exchange.mutate().request(builder.build()).build();
    }

    private Mono<Void> applyRateLimitOrContinue(ServerWebExchange exchange, GatewayFilterChain chain,
                                                  String method, String clientIp, ApiRouteDto routeConfig) {
        return rateLimiterService.isAllowed(method, clientIp, routeConfig)
            .flatMap(allowed -> {
                if (Boolean.FALSE.equals(allowed)) {
                    return errorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.ERR_00429,
                        "Rate limit exceeded. Please try again later.");
                }
                return chain.filter(exchange);
            });
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst(Constants.X_FORWARDED_FOR);
        if (StringUtils.isNotBlank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return Optional.ofNullable(request.getRemoteAddress())
            .map(addr -> addr.getAddress().getHostAddress())
            .orElse(Constants.UNKNOWN);
    }

    private String resolveCorrelationId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(Constants.CORRELATION_ID);
        return StringUtils.isNotBlank(existing) ? existing : UUID.randomUUID().toString();
    }

    private Mono<Void> errorResponse(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);

        MessageResponse msg = new MessageResponse();
        msg.setCode(errorCode);
        msg.setMessage(message);
        msg.setSuccess(false);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setData(Collections.emptyMap());
        msg.setTraceId(getTraceId());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(msg);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write error response", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }

    private boolean isRouteAvailableNow(ApiRouteDto route) {
        LocalDateTime now = LocalDateTime.now();
        return (route.getStartTime() == null || !now.isBefore(route.getStartTime())) &&
               (route.getEndTime()   == null || !now.isAfter(route.getEndTime()));
    }

    private String getTraceId() {
        return tracer != null && tracer.currentSpan() != null
            ? Objects.requireNonNull(tracer.currentSpan()).context().traceId()
            : UUID.randomUUID().toString();
    }
}
