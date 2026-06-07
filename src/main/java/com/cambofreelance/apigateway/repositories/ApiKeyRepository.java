package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.ApiKey;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApiKeyRepository extends R2dbcRepository<ApiKey, Long> {

    @Query("SELECT * FROM public.api_key WHERE status = 'ACT' AND key_prefix = :prefix ORDER BY id")
    Flux<ApiKey> findActiveByPrefix(String prefix);

    @Query("SELECT * FROM public.api_key WHERE status = :status ORDER BY created_at DESC")
    Flux<ApiKey> findAllByStatus(String status);

    @Query("UPDATE public.api_key SET last_used_at = NOW() WHERE id = :id")
    Mono<Integer> touchLastUsed(Long id);
}