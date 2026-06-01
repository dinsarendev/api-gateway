package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.ApiGroupRoute;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApiGroupRouteRepository extends R2dbcRepository<ApiGroupRoute, Long> {

    Flux<ApiGroupRoute> findAllByStatus(String status);

    Mono<ApiGroupRoute> findByCode(String code);

    Mono<Boolean> existsByCode(String code);
}