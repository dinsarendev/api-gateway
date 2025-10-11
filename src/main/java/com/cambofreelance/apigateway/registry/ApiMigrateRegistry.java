package com.cambofreelance.apigateway.registry;

import java.util.List;
import com.cambofreelance.apigateway.caches.ApiRouteManagerCache;
import com.cambofreelance.apigateway.caches.ApiRouteManagerRedisCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import com.cambofreelance.apigateway.models.ApiRoute;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
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
    private final ApiRouteManagerRedisCache apiRouteManagerRedisCache;

    public void loadComponent() {
        log.info("Loading component ...");
        this.loadApiRouteCache();
    }

    private void loadApiRouteCache() {
        try {
            log.info("loading api route  ...");
            List<ApiRoute> apiRouteList = apiRouteRepository.findAllByStatus(
                    Constants.STATUS_ACTIVE)
                .filter(route -> route.getStatus().equals(Constants.STATUS_ACTIVE))
                .collectList()
                .block();
            log.info("{} api route code loaded successfully", apiRouteList);
            if (apiRouteList == null || apiRouteList.isEmpty()) {
                log.warn("No API routes found to load.");
                return;
            }
            List<ApiRouteDto> apiRouteDtoList = apiRouteList.stream()
                .map(apiRoute -> {
                    ApiRouteDto apiRouteDto = new ApiRouteDto();
                    apiRouteDto.setData(apiRoute);
                    return apiRouteDto;
                })
                .toList();
            log.info("loading local cache api route  ...");
            ApiRouteManagerCache.init(apiRouteDtoList);
            Thread threadLoadResponseCode = new Thread(() -> {
                apiRouteManagerRedisCache.initCache(apiRouteDtoList);

            });
            log.info("finish local cache api route  ...");
            threadLoadResponseCode.start();
            log.info("finish api route  ...");
        } catch (Throwable e) {
            log.error("Error loading API routes: {}", e.getMessage(), e);
        }
    }

}
