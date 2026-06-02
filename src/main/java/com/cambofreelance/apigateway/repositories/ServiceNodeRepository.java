package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.ServiceNode;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ServiceNodeRepository extends R2dbcRepository<ServiceNode, Long> {

    /** All records that haven't been soft-deleted (status = 'ACT'). */
    Flux<ServiceNode> findAllByStatus(String status);

    /** Update the runtime health status and probe timestamp for one instance. */
    @Modifying
    @Query("""
        UPDATE service_instance
           SET health_status      = :healthStatus,
               last_health_check  = :lastHealthCheck,
               updated_at         = :updatedAt
         WHERE id = :id
        """)
    Mono<Integer> updateHealthStatus(Long id,
                                     String healthStatus,
                                     LocalDateTime lastHealthCheck,
                                     LocalDateTime updatedAt);

    /** Full field update for an existing instance (excluding health status). */
    @Modifying
    @Query("""
        UPDATE service_instance
           SET service_id   = :serviceId,
               host         = :host,
               port         = :port,
               secure       = :secure,
               weight       = :weight,
               health_path  = :healthPath,
               updated_at   = :updatedAt,
               updated_by   = :updatedBy
         WHERE id = :id
        """)
    Mono<Integer> updateInstance(Long id,
                                 String serviceId, String host, Integer port,
                                 boolean secure, Integer weight, String healthPath,
                                 LocalDateTime updatedAt, String updatedBy);

    /** Soft-delete: mark inactive. */
    @Modifying
    @Query("""
        UPDATE service_instance
           SET status = 'INACT', updated_at = :updatedAt, updated_by = :updatedBy
         WHERE id = :id
        """)
    Mono<Integer> softDelete(Long id, LocalDateTime updatedAt, String updatedBy);
}