package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.ApiRoute;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ApiRouteRepository extends R2dbcRepository<ApiRoute, Long> {

    @Query("""
        SELECT a.*, g.uri AS uri
          FROM public.api_route a
          INNER JOIN public.api_group_route g ON g.code = a.group_code
          WHERE a.path = :path AND a.method = :method AND a.status = 'ACT'
        """)
    Mono<ApiRoute> findFirstByPathAndMethod(String path, String method);

    @Query("""
        SELECT a.*, g.uri AS uri
          FROM public.api_route a
          LEFT JOIN public.api_group_route g ON g.code = a.group_code
          WHERE a.status = :status
          ORDER BY a.priority ASC NULLS LAST, a.id ASC
        """)
    Flux<ApiRoute> findAllByStatus(String status);

    @Query("""
        SELECT a.*, g.uri AS uri
          FROM public.api_route a
          LEFT JOIN public.api_group_route g ON g.code = a.group_code
          WHERE a.id = :id
        """)
    Mono<ApiRoute> findByIdWithUri(Long id);

    @Query("""
        SELECT COUNT(*) FROM public.api_route WHERE status = :status
        """)
    Mono<Long> countByStatus(String status);

    @Modifying
    @Query("""
        UPDATE public.api_route
           SET group_code             = :groupCode,
               path                   = :path,
               method                 = :method,
               description            = :description,
               application_id         = :applicationId,
               is_public              = :isPublic,
               is_encrypt             = :isEncrypt,
               enable_circuit_breaker = :enableCircuitBreaker,
               rate_limit             = :rateLimit,
               rate_limit_duration    = :rateLimitDuration,
               priority               = :priority,
               start_time             = :startTime,
               end_time               = :endTime,
               auth_type              = :authType,
               required_roles         = :requiredRoles,
               required_permissions   = :requiredPermissions,
               updated_at             = :updatedAt,
               updated_by             = :updatedBy
         WHERE id = :id
        """)
    Mono<Integer> updateRoute(Long id,
                              String groupCode, String path, String method,
                              String description, String applicationId,
                              String isPublic, String isEncrypt, String enableCircuitBreaker,
                              Integer rateLimit, Integer rateLimitDuration,
                              Integer priority, LocalDateTime startTime, LocalDateTime endTime,
                              String authType, String requiredRoles, String requiredPermissions,
                              LocalDateTime updatedAt, String updatedBy);

    @Modifying
    @Query("""
        UPDATE public.api_route
           SET status = :status, updated_at = :updatedAt, updated_by = :updatedBy
         WHERE id = :id
        """)
    Mono<Integer> updateStatus(Long id, String status, LocalDateTime updatedAt, String updatedBy);
}
