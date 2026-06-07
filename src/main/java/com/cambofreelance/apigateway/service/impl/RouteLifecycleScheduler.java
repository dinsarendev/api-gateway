package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteLifecycleScheduler {

    private final ApiRouteRepository apiRouteRepository;
    private final GatewayRouteService gatewayRouteService;

    @Scheduled(fixedDelay = 60_000)
    public void autoRetirePastSunset() {
        apiRouteRepository.findDeprecatedPastSunset(LocalDateTime.now())
            .flatMap(route -> {
                log.info("Auto-retiring route id={} {} {} (sunset_date={})",
                    route.getId(), route.getMethod(), route.getPath(), route.getSunsetDate());
                return apiRouteRepository.updateStatus(
                    route.getId(), Constants.STATUS_RETIRED, LocalDateTime.now(), Constants.SYSTEM);
            })
            .count()
            .filter(count -> count > 0)
            .doOnNext(count -> {
                log.info("Route lifecycle: auto-retired {} route(s); refreshing gateway", count);
                gatewayRouteService.refreshRoutes();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(null, e -> log.error("Route lifecycle scheduler error: {}", e.getMessage()));
    }
}
