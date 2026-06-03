package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AuditLog;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends R2dbcRepository<AuditLog, Long> {

    @Query("""
        SELECT * FROM public.audit_log
         WHERE (:module IS NULL OR module   = :module)
           AND (:action IS NULL OR action   = :action)
           AND (:actor  IS NULL OR actor ILIKE :actor)
           AND (:result IS NULL OR result   = :result)
           AND (:from   IS NULL OR created_at >= :from)
           AND (:to     IS NULL OR created_at <= :to)
         ORDER BY created_at DESC
         LIMIT :size OFFSET :offset
        """)
    Flux<AuditLog> findByFilter(String module, String action, String actor, String result,
                                 LocalDateTime from, LocalDateTime to, int size, long offset);

    @Query("""
        SELECT COUNT(*) FROM public.audit_log
         WHERE (:module IS NULL OR module   = :module)
           AND (:action IS NULL OR action   = :action)
           AND (:actor  IS NULL OR actor ILIKE :actor)
           AND (:result IS NULL OR result   = :result)
           AND (:from   IS NULL OR created_at >= :from)
           AND (:to     IS NULL OR created_at <= :to)
        """)
    Mono<Long> countByFilter(String module, String action, String actor, String result,
                              LocalDateTime from, LocalDateTime to);
}