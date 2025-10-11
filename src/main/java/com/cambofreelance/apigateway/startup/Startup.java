package com.cambofreelance.apigateway.startup;

import com.cambofreelance.apigateway.registry.ApiMigrateRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Startup {

    private final ApiMigrateRegistry apiMngRegistry;

    private void init() {
        apiMngRegistry.loadComponent();
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void initApiMigrate() {
        log.info("Setting up Api route Manager context ...");
        this.init();
    }
}
