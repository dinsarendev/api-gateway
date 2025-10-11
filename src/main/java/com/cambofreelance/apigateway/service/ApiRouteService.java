package com.cambofreelance.apigateway.service;//package com.cambofreelance.apigateway.service;
//
//import com.cambofreelance.apigateway.dto.RouteApiRequest;
//import com.cambofreelance.apigateway.dto.RouteApiResponse;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//public interface ApiRouteService {
//
//    Mono<RouteApiResponse> create(RouteApiRequest routeApiRequest);
//
//    Mono<RouteApiResponse> update(Long id, RouteApiRequest routeApiRequest);
//
//    Flux<RouteApiResponse> findAll();
//
//    Mono<RouteApiResponse> findById(Long id);
//
//    Mono<Void> delete(Long id);
//
//    Mono<Void> deleteAll();
//}
