package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.OAuth2Provider;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OAuth2ProviderRepository extends R2dbcRepository<OAuth2Provider, Long> {

    @Query("SELECT * FROM public.oauth2_provider WHERE status = 'ACT' LIMIT 1")
    Mono<OAuth2Provider> findFirstActive();

    @Query("SELECT * FROM public.oauth2_provider WHERE status = :status ORDER BY id")
    Flux<OAuth2Provider> findAllByStatus(String status);
}