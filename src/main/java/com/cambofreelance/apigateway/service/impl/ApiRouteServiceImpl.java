package com.cambofreelance.apigateway.service.impl;//package com.cambofreelance.apigateway.service.impl;
//
//import com.cambofreelance.apigateway.dto.RouteApiRequest;
//import com.cambofreelance.apigateway.dto.RouteApiResponse;
//import com.cambofreelance.apigateway.exception.RouteCreationException;
//import com.cambofreelance.apigateway.exception.RouteNotFoundException;
//import com.cambofreelance.apigateway.models.ApiRoute;
//import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
//import com.cambofreelance.apigateway.service.ApiRouteService;
//import java.time.LocalDate;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//@Service
//@Slf4j
//public class ApiRouteServiceImpl implements ApiRouteService {
//
//    private final ApiRouteRepository apiRouteRepository;
//    private final GatewayRouteService gatewayRouteService;
//
//    public ApiRouteServiceImpl(ApiRouteRepository apiRouteRepository,
//                               GatewayRouteService gatewayRouteService) {
//        this.apiRouteRepository = apiRouteRepository;
//        this.gatewayRouteService = gatewayRouteService;
//    }
//
//
//    @Override
//    public Mono<RouteApiResponse> create(RouteApiRequest routeApiRequest) {
//        ApiRoute apiRoute = convertRouteApiRequestToApiRoute(routeApiRequest);
//        apiRoute.setUpdatedAt(null);
//        apiRoute.setUpdatedBy(null);
//        return apiRouteRepository.save(apiRoute)
//                .doOnSuccess(newRoute -> gatewayRouteService.refreshRoutes())
//                .map(this::convertApiRouteToRouteApiResponse)
//                .onErrorMap(e -> {
//                    log.error("Error occurred while creating route: {}", e.getMessage());
//                    throw new RouteCreationException("An error occurred while creating route");
//                });
//    }
//
//    @Override
//    public Mono<RouteApiResponse> update(Long id, RouteApiRequest routeApiRequest) {
//        return apiRouteRepository.updateRoute(
//                        id,
//                        routeApiRequest.uri(),
//                        routeApiRequest.path(),
//                        routeApiRequest.method(),
//                        routeApiRequest.description(),
//                        routeApiRequest.groupCode(),
//                        routeApiRequest.status(),
//                        "admin")
//                .switchIfEmpty(Mono.error(new RouteNotFoundException("Route with id " + id + " not found")))
//                .doOnSuccess(updatedRoute -> gatewayRouteService.refreshRoutes())
//                .map(this::convertApiRouteToRouteApiResponse);
//    }
//
//    @Override
//    public Flux<RouteApiResponse> findAll() {
//        return apiRouteRepository.findAll()
//                .map(this::convertApiRouteToRouteApiResponse)
//                .onErrorResume(e -> {
//                    log.error("Error occurred while fetching all routes: {}", e.getMessage());
//                    throw new RouteNotFoundException("Route not found");
//                });
//    }
//
//    @Override
//    public Mono<RouteApiResponse> findById(Long id) {
//        return apiRouteRepository.findFirstById(id)  // Use findById to find the route
//                .switchIfEmpty(Mono.error(new RouteNotFoundException("Route with id " + id + " not found")))  // Handle case when no route is found
//                .map(this::convertApiRouteToRouteApiResponse)  // Convert the result to the response DTO
//                .onErrorResume(RouteNotFoundException.class, e -> {
//                    log.error("Find Route by id not found: {}", e.getMessage());
//                    return Mono.error(e);  // Propagate the exception to be handled globally
//                })
//                .onErrorResume(e -> {
//                    log.error("Unexpected error occurred while fetching route by id: {}", e.getMessage());
//                    return Mono.error(new RuntimeException("Failed to fetch route", e));  // Propagate generic error
//                });
//    }
//
//    @Override
//    public Mono<Void> delete(Long id) {
//        return apiRouteRepository.findFirstById(id) // Check if the entity exists
//                .switchIfEmpty(Mono.error(new RouteNotFoundException("Route with id " + id + " not found")))
//                .flatMap(route -> apiRouteRepository.deleteAllById(id)) // Proceed to delete if found
//                .then(Mono.fromRunnable(gatewayRouteService::refreshRoutes)) // Refresh routes after deletion
//                .onErrorResume(RouteNotFoundException.class, e -> {
//                    log.error("Route not found: {}", e.getMessage());
//                    return Mono.error(e); // Propagate the exception for global handling
//                })
//                .onErrorResume(e -> {
//                    log.error("Unexpected error occurred while deleting route: {}", e.getMessage());
//                    return Mono.error(new RuntimeException("Failed to delete route", e));
//                }).then();
//    }
//
//    @Override
//    public Mono<Void> deleteAll() {
//        return apiRouteRepository.deleteAll()
//                .doOnSuccess(deletedRoutes -> gatewayRouteService.refreshRoutes())
//                .onErrorResume(e -> {
//                    log.error("Error occurred while deleting all routes: {}", e.getMessage());
//                    return Mono.empty();
//                });
//    }
//
//    public ApiRoute convertRouteApiRequestToApiRoute(RouteApiRequest apiRoute) {
//        return new ApiRoute(apiRoute.id(),
//                            apiRoute.uri(),
//                            apiRoute.path(),
//                            apiRoute.method(),
//                            apiRoute.description(),
//                            apiRoute.applicationId(),
//                            apiRoute.rateLimit(),
//                            apiRoute.rateLimitDuration(),
//                            apiRoute.status(),
//                "admin",
//                LocalDate.now(),
//                "admin",
//            LocalDate.now(),
//            apiRoute.isPublic() != null ? apiRoute.isPublic() : "N"
//                );
//    }
//
//    public RouteApiResponse convertApiRouteToRouteApiResponse(ApiRoute apiRoute) {
//        return new RouteApiResponse(apiRoute.getId(),
//                                    apiRoute.getUri(),
//                                    apiRoute.getPath(),
//                                    apiRoute.getMethod(),
//                                    apiRoute.getDescription(),
//                                    apiRoute.getApplicationId(),
//                                    apiRoute.getRateLimit(),
//                                    apiRoute.getRateLimitDuration(),
//                                    apiRoute.getStatus(),
//                                    apiRoute.getCreatedBy(),
//                                    apiRoute.getCreatedAt().toString(),
//                                    apiRoute.getUpdatedBy(),
//                                    apiRoute.getUpdatedAt() == null ?
//                                            null : apiRoute.getUpdatedAt().toString());
//    }
//}
