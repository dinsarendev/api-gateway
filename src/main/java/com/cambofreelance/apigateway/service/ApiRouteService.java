package com.cambofreelance.apigateway.service;

import com.cambofreelance.apigateway.dto.RouteApiRequest;
import com.cambofreelance.apigateway.dto.RouteApiResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ApiRouteService {

    Mono<RouteApiResponse> create(RouteApiRequest request);

    Mono<RouteApiResponse> update(Long id, RouteApiRequest request);

    Flux<RouteApiResponse> findAll();

    Flux<RouteApiResponse> findAllByStatus(String status);

    Mono<RouteApiResponse> findById(Long id);

    Mono<Void> delete(Long id);

    Mono<Void> enable(Long id);

    Mono<Void> disable(Long id);

    Mono<Void> reloadRoutes();

    Mono<Void> deprecate(Long id, LocalDateTime sunsetDate);

    Mono<Void> undeprecate(Long id);

    Mono<Void> retire(Long id);
}
