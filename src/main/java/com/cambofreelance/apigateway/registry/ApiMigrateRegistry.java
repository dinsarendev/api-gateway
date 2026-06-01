package com.cambofreelance.apigateway.registry;

import java.util.List;
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

@RefreshScope
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiMigrateRegistry {

    private final ApiRouteRepository apiRouteRepository;
    private final ServiceNodeRepository serviceNodeRepository;
    private final ApiRouteManagerRedisCache apiRouteManagerRedisCache;
    private final IpAccessControlRepository ipAccessControlRepository;

    public void loadComponent() {
        log.info("Loading registry components ...");
        loadApiRouteCache();
        loadServiceInstanceCache();
        loadIpAclCache();
    }

    // ── Route cache ────────────────────────────────────────────────────────────

    private void loadApiRouteCache() {
        try {
            List<ApiRoute> routes = apiRouteRepository
                .findAllByStatus(Constants.STATUS_ACTIVE)
                .collectList()
                .block();

            if (routes == null || routes.isEmpty()) {
                log.warn("No active API routes found.");
                return;
            }

            List<ApiRouteDto> dtoList = routes.stream()
                .map(r -> { ApiRouteDto dto = new ApiRouteDto(); dto.setData(r); return dto; })
                .toList();

            ApiRouteManagerCache.init(dtoList);
            log.info("Route cache initialized: {} routes", dtoList.size());

            new Thread(() -> apiRouteManagerRedisCache.initCache(dtoList), "redis-route-init").start();

        } catch (Throwable e) {
            log.error("Failed to load route cache: {}", e.getMessage(), e);
        }
    }

    // ── IP ACL cache ───────────────────────────────────────────────────────────

    private void loadIpAclCache() {
        try {
            List<IpAccessControl> rules = ipAccessControlRepository
                .findAllByStatus(Constants.STATUS_ACTIVE)
                .collectList()
                .block();
            IpAclCache.init(rules);
            log.info("IP ACL cache initialized: {} rule(s)", rules != null ? rules.size() : 0);
        } catch (Throwable e) {
            log.error("Failed to load IP ACL cache: {}", e.getMessage(), e);
        }
    }

    // ── Service instance cache ─────────────────────────────────────────────────

    private void loadServiceInstanceCache() {
        try {
            List<ServiceNode> nodes = serviceNodeRepository
                .findAllByStatus(Constants.STATUS_ACTIVE)
                .collectList()
                .block();

            ServiceInstanceCache.init(nodes == null ? List.of() : nodes);

            if (nodes == null || nodes.isEmpty()) {
                log.info("No active service instances — lb:// routes will resolve to empty.");
            } else {
                log.info("Service instance cache initialized: {} instance(s) across service(s): {}",
                    nodes.size(), ServiceInstanceCache.getServiceIds());
            }
        } catch (Throwable e) {
            log.error("Failed to load service instance cache: {}", e.getMessage(), e);
        }
    }
}
