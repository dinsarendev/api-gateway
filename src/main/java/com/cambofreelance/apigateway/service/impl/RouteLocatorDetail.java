package com.cambofreelance.apigateway.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import com.cambofreelance.apigateway.configs.DecryptRequestBodyFilter;
import com.cambofreelance.apigateway.configs.EncryptResponseBodyFilter;
import com.cambofreelance.apigateway.constants.Constants;
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
        if (StringUtils.isNotBlank(apiRoute.getMethod())) {
            booleanSpec = booleanSpec.and().method(apiRoute.getMethod());
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
                    return f;
                })
                .uri(apiRoute.getUri());
    }
}
