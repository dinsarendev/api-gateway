package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.ApiGroupRoute;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ApiGroupRouteRepository extends R2dbcRepository<ApiGroupRoute, Long> {

    Flux<ApiGroupRoute> findAllByStatus(String status);

    Mono<ApiGroupRoute> findByCode(String code);

    Mono<Boolean> existsByCode(String code);

    @Modifying
    @Query("""
        UPDATE public.api_group_route
           SET active_slot = CASE WHEN active_slot = 'BLUE' THEN 'GREEN' ELSE 'BLUE' END,
               updated_at  = :updatedAt,
               updated_by  = :updatedBy
         WHERE code = :code
        """)
    Mono<Integer> swapSlot(String code, LocalDateTime updatedAt, String updatedBy);

    @Modifying
    @Query("""
        UPDATE public.api_group_route
           SET blue_uri   = COALESCE(:blueUri,  blue_uri),
               green_uri  = COALESCE(:greenUri, green_uri),
               updated_at = :updatedAt,
               updated_by = :updatedBy
         WHERE code = :code
        """)
    Mono<Integer> configureSlots(String code, String blueUri, String greenUri,
                                 LocalDateTime updatedAt, String updatedBy);
}