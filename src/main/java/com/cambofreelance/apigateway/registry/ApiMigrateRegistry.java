package com.cambofreelance.apigateway.registry;

import com.cambofreelance.apigateway.caches.ApiRouteManagerCache;
import com.cambofreelance.apigateway.caches.ApiRouteManagerRedisCache;
import com.cambofreelance.apigateway.caches.IpAclCache;
import com.cambofreelance.apigateway.caches.ServiceInstanceCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import com.cambofreelance.apigateway.models.ApiRoute;
import com.cambofreelance.apigateway.models.IpAccessControl;
import com.cambofreelance.apigateway.models.ServiceNode;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
import com.cambofreelance.apigateway.repositories.IpAccessControlRepository;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@RefreshScope
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiMigrateRegistry {

    private final ApiRouteRepository apiRouteRepository;
    private final ServiceNodeRepository serviceNodeRepository;
    private final ApiRouteManagerRedisCache apiRouteManagerRedisCache;
    private final IpAccessControlRepository ipAccessControlRepository;

    public Mono<Void> loadComponent() {
        log.info("Loading registry components ...");
        return loadApiRouteCache()
            .then(loadServiceInstanceCache())
            .then(loadIpAclCache());
    }

    // ── Route cache ────────────────────────────────────────────────────────────

    private Mono<Void> loadApiRouteCache() {
        return apiRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .doOnNext(routes -> {
                List<ApiRouteDto> dtoList = routes.stream()
                    .map(r -> { ApiRouteDto dto = new ApiRouteDto(); dto.setData(r); return dto; })
                    .toList();
                ApiRouteManagerCache.init(dtoList);
                apiRouteManagerRedisCache.initCache(dtoList);
                log.info("Route cache initialized: {} routes", dtoList.size());
            })
            .doOnError(e -> log.error("Failed to load route cache: {}", e.getMessage(), e))
            .onErrorResume(e -> Mono.empty())
            .then();
    }

    // ── IP ACL cache ───────────────────────────────────────────────────────────

    private Mono<Void> loadIpAclCache() {
        return ipAccessControlRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .doOnNext(rules -> {
                IpAclCache.init(rules);
                log.info("IP ACL cache initialized: {} rule(s)", rules.size());
            })
            .doOnError(e -> log.error("Failed to load IP ACL cache: {}", e.getMessage(), e))
            .onErrorResume(e -> Mono.empty())
            .then();
    }

    // ── Service instance cache ─────────────────────────────────────────────────

    private Mono<Void> loadServiceInstanceCache() {
        return serviceNodeRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .doOnNext(nodes -> {
                ServiceInstanceCache.init(nodes == null ? List.of() : nodes);
                if (nodes == null || nodes.isEmpty()) {
                    log.info("No active service instances — lb:// routes will resolve to empty.");
                } else {
                    log.info("Service instance cache initialized: {} instance(s) across service(s): {}",
                        nodes.size(), ServiceInstanceCache.getServiceIds());
                }
            })
            .doOnError(e -> log.error("Failed to load service instance cache: {}", e.getMessage(), e))
            .onErrorResume(e -> Mono.empty())
            .then();
    }
}
