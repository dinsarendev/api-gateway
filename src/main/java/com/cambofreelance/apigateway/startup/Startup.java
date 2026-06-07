package com.cambofreelance.apigateway.startup;

import com.cambofreelance.apigateway.registry.ApiMigrateRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class Startup {

    private final ApiMigrateRegistry apiMngRegistry;

    /** Called from @PostConstruct on the main thread — blocking is intentional. */
    public void initApiMigrate() {
        log.info("Setting up Api route Manager context ...");
        apiMngRegistry.loadComponent()
            .subscribeOn(Schedulers.boundedElastic())
            .block();
    }

    /** Async refresh when cloud config changes. */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onConfigRefresh() {
        log.info("Config refreshed — reloading registry ...");
        apiMngRegistry.loadComponent()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(null, e -> log.error("Registry reload failed: {}", e.getMessage()));
    }
}
