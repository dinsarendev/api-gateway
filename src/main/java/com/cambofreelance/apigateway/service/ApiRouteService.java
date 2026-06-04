package com.cambofreelance.apigateway.service;

import com.cambofreelance.apigateway.dto.RouteApiRequest;
import com.cambofreelance.apigateway.dto.RouteApiResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ApiRouteService {

    Mono<RouteApiResponse> create(RouteApiRequest request, String actor);

    Mono<RouteApiResponse> update(Long id, RouteApiRequest request, String actor);

    Flux<RouteApiResponse> findAll();

    Flux<RouteApiResponse> findAllByStatus(String status);

    Mono<RouteApiResponse> findById(Long id);

    Mono<Void> delete(Long id, String actor);

    Mono<Void> enable(Long id, String actor);

    Mono<Void> disable(Long id, String actor);

    Mono<Void> reloadRoutes();

    Mono<Void> deprecate(Long id, LocalDateTime sunsetDate, String actor);

    Mono<Void> undeprecate(Long id, String actor);

    Mono<Void> retire(Long id, String actor);

    Mono<Void> submit(Long id, String actor);

    Mono<Void> approve(Long id, String actor);

    Mono<Void> reject(Long id, String reason, String actor);
}
