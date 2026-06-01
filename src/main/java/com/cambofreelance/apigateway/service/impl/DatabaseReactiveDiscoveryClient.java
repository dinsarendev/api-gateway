package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.caches.ServiceInstanceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class DatabaseReactiveDiscoveryClient implements ReactiveDiscoveryClient {

    @Override
    public String description() {
        return "Database-backed Service Discovery";
    }

    /**
     * Returns all UP instances for the given service ID.
     * Called by Spring Cloud LoadBalancer on every lb:// route resolution.
     * Reads from the in-memory ServiceInstanceCache (populated at startup).
     */
    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return Flux.fromIterable(ServiceInstanceCache.getInstances(serviceId))
            .filter(node -> "UP".equalsIgnoreCase(node.getHealthStatus()))
            .map(node -> {
                log.debug("Resolving instance: serviceId={}, host={}, port={}, secure={}",
                    node.getServiceId(), node.getHost(), node.getPort(), node.isSecure());
                return (ServiceInstance) new DefaultServiceInstance(
                    String.valueOf(node.getId()),
                    node.getServiceId(),
                    node.getHost(),
                    node.getPort(),
                    node.isSecure()
                );
            })
            .doOnComplete(() -> log.debug("Discovery resolved {} instance(s) for '{}'",
                ServiceInstanceCache.getInstances(serviceId).size(), serviceId));
    }

    @Override
    public Flux<String> getServices() {
        return Flux.fromIterable(ServiceInstanceCache.getServiceIds());
    }
}