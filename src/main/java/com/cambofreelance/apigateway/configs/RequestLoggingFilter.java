package com.cambofreelance.apigateway.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import com.cambofreelance.apigateway.caches.ApiRouteManagerCache;
import com.cambofreelance.apigateway.caches.ApiRouteManagerRedisCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.ErrorCode;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import com.cambofreelance.apigateway.exception.MessageResponse;
import com.cambofreelance.apigateway.service.impl.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter {

    private final RateLimiterService rateLimiterService;
    private final ApiRouteManagerRedisCache apiRouteManagerRedisCache;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public RequestLoggingFilter(RateLimiterService rateLimiterService,
                                ApiRouteManagerRedisCache apiRouteManagerRedisCache,
                                ObjectMapper objectMapper,
                                Tracer tracer) {
        this.rateLimiterService = rateLimiterService;
        this.apiRouteManagerRedisCache = apiRouteManagerRedisCache;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String uri = request.getURI().toString();
        String path = request.getPath().toString();
        String method = Optional.of(request.getMethod())
                .map(Object::toString)
                .orElse(Constants.UNKNOWN);

        String clientIp = resolveClientIp(request);

        log.info("Request received: uri={}, path={}, method={}, clientIp={}", uri, path, method, clientIp);

        ApiRouteDto apiRouteDto = safeGetApiRouteFromRedis(path, method);
        boolean redisRouteFound = apiRouteDto != null;

        if (Objects.isNull(apiRouteDto)) {
            apiRouteDto = ApiRouteManagerCache.getByPath(path);
        }

        if (Objects.isNull(apiRouteDto)) {
            return errorResponse(exchange, HttpStatus.NOT_FOUND, ErrorCode.ERR_00404, "API route not found in cache");
        }

        if (Constants.YES.equalsIgnoreCase(apiRouteDto.getIsPublic())
                && method.equalsIgnoreCase(apiRouteDto.getMethod())) {
            return redisRouteFound
                    ? applyRateLimitOrContinue(exchange, chain, path, method, clientIp)
                    : chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(authHeader)) {
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.ERR_00401, "Missing Authorization header");
        }

        if (!authHeader.startsWith(Constants.BEARER)) {
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.ERR_00401, "Invalid Authorization header format");
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(Constants.IP, clientIp)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return redisRouteFound
                ? applyRateLimitOrContinue(mutatedExchange, chain, path, method, clientIp)
                : chain.filter(mutatedExchange);
    }

    private String resolveClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String forwardedFor = headers.getFirst(Constants.X_FORWARDED_FOR);
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return Optional.ofNullable(request.getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse(Constants.UNKNOWN);
    }

    private Mono<Void> applyRateLimitOrContinue(ServerWebExchange exchange, GatewayFilterChain chain,
                                                String path, String method, String clientIp) {
        return rateLimiterService.isAllowed(path, method, clientIp)
                .flatMap(isAllowed -> {
                    if (Boolean.FALSE.equals(isAllowed)) {
                        return errorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.ERR_00429,
                                "Rate limit exceeded. Please try again later.");
                    }
                    return chain.filter(exchange);
                });
    }

    private ApiRouteDto safeGetApiRouteFromRedis(String path, String method) {
        try {
            return apiRouteManagerRedisCache.getPathAndMethod(path, method);
        } catch (Exception e) {
            log.error("Redis error fetching route [{} {}]: {}", path, method, e.getMessage(), e);
            return null;
        }
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
            log.error("Failed to serialize MessageResponse", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }

    private String getTraceId() {
        return tracer != null && tracer.currentSpan() != null
                ? Objects.requireNonNull(tracer.currentSpan()).context().traceId()
                : UUID.randomUUID().toString();
    }
}
