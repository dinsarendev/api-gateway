package com.cambofreelance.apigateway.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import com.cambofreelance.apigateway.configs.DecryptRequestBodyFilter;
import com.cambofreelance.apigateway.configs.EncryptResponseBodyFilter;
import com.cambofreelance.apigateway.constants.Constants;
import java.time.Duration;
import com.cambofreelance.apigateway.models.ApiRoute;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteLocatorDetail implements RouteLocator {

    private final RouteLocatorBuilder routeLocatorBuilder;
    private final ApiRouteRepository apiRouteRepository;

    @Autowired
    private DecryptRequestBodyFilter decryptRequestBodyFilter;

    @Autowired
    private EncryptResponseBodyFilter encryptResponseBodyFilter;

    @Override
    public Flux<Route> getRoutes() {
        RouteLocatorBuilder.Builder builder = routeLocatorBuilder.routes();

        return apiRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
                .filter(this::isRouteAvailableNow)
                .sort(Comparator.comparing(ApiRoute::getPriority, Comparator.nullsLast(Integer::compareTo)))
                .map(route -> builder.route(route.getId().toString(), spec -> setPredicateSpec(route, spec)))
                .collectList()
                .flatMapMany(_ignored -> builder.build().getRoutes());
    }

    private boolean isRouteAvailableNow(ApiRoute route) {
        LocalDateTime now = LocalDateTime.now();
        return (route.getStartTime() == null || !now.isBefore(route.getStartTime())) &&
                (route.getEndTime() == null || !now.isAfter(route.getEndTime()));
    }

    private Buildable<Route> setPredicateSpec(ApiRoute apiRoute, PredicateSpec predicateSpec) {
        BooleanSpec booleanSpec = predicateSpec.path(apiRoute.getPath());
        String apiType = apiRoute.getApiType() != null ? apiRoute.getApiType().toUpperCase() : Constants.API_TYPE_REST;

        // GraphQL: allow both GET and POST when no method is explicitly set
        if (Constants.API_TYPE_GRAPHQL.equals(apiType) && StringUtils.isBlank(apiRoute.getMethod())) {
            booleanSpec = booleanSpec.and().method("GET", "POST");
        } else if (StringUtils.isNotBlank(apiRoute.getMethod())) {
            booleanSpec = booleanSpec.and().method(apiRoute.getMethod());
        }

        // Streaming/AI: set 30-min response timeout via route metadata
        // (GatewayFilterSpec has no setResponseTimeout; metadata is the correct API)
        if (Constants.API_TYPE_STREAMING.equals(apiType) || Constants.API_TYPE_AI.equals(apiType)) {
            booleanSpec = (BooleanSpec) booleanSpec.metadata("response-timeout", Duration.ofMinutes(30));
        }

        return booleanSpec
                .filters(f -> {
                    if (Constants.YES.equalsIgnoreCase(apiRoute.getIsEncrypt())) {
                        f.filter(decryptRequestBodyFilter.apply(new DecryptRequestBodyFilter.Config()));
                        f.filter(encryptResponseBodyFilter.apply(new EncryptResponseBodyFilter.Config()));
                    }
                    if (Constants.YES.equalsIgnoreCase(apiRoute.getEnableCircuitBreaker())) {
                        f.circuitBreaker(c -> {
                            c.setName(apiRoute.getGroupCode() + "-breaker");
                            c.setFallbackUri("forward:/fallback/" + apiRoute.getGroupCode());
                        });
                    }
                    applyApiTypeFilters(f, apiType);
                    return f;
                })
                .uri(apiRoute.getUri());
    }

    private void applyApiTypeFilters(
            org.springframework.cloud.gateway.route.builder.GatewayFilterSpec f,
            String apiType) {
        switch (apiType) {
            case Constants.API_TYPE_SOAP ->
                // SOAP services expect XML content type
                f.addRequestHeader("Content-Type", "text/xml;charset=UTF-8");
            case Constants.API_TYPE_STREAMING, Constants.API_TYPE_AI -> {
                // SSE/streaming: disable buffering and remove chunked-encoding header
                // so browsers receive events as they arrive
                f.removeResponseHeader("Transfer-Encoding");
                f.addResponseHeader("X-Accel-Buffering", "no");
                f.addResponseHeader("Cache-Control", "no-cache");
            }
            default -> { /* REST / GRAPHQL: no additional filters */ }
        }
    }
}
