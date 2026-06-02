package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.Incident;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface IncidentRepository extends R2dbcRepository<Incident, Long> {

    Flux<Incident> findAllByStatusOrderByOpenedAtDesc(String status);

    Flux<Incident> findAllByOrderByOpenedAtDesc();

    Mono<Long> countByStatus(String status);

    Mono<Long> countBySeverityAndStatusNot(String severity, String status);

    // ── Auto-incident deduplication ────────────────────────────────────────────

    @Query("SELECT * FROM public.incident WHERE trigger_key = :triggerKey AND status IN ('OPEN','INVESTIGATING') LIMIT 1")
    Mono<Incident> findOpenByTriggerKey(String triggerKey);

    // ── Dashboard aggregates ───────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (resolved_at - opened_at)) / 60), 0)
          FROM public.incident
         WHERE status = 'RESOLVED'
           AND resolved_at >= :since
        """)
    Mono<Double> avgMttrMinutesSince(LocalDateTime since);

    @Query("""
        SELECT COUNT(*) FROM public.incident
         WHERE resolved_at >= :since AND status IN ('RESOLVED','CLOSED')
        """)
    Mono<Long> countResolvedSince(LocalDateTime since);

    @Query("""
        SELECT opened_at FROM public.incident
         WHERE opened_at >= :since
         ORDER BY opened_at ASC
        """)
    Flux<LocalDateTime> findOpenedAtSince(LocalDateTime since);

    // ── Status/severity counts ─────────────────────────────────────────────────

    @Modifying
    @Query("""
        UPDATE public.incident
           SET status = :status, updated_at = :updatedAt, updated_by = :updatedBy,
               resolved_at = CASE WHEN :status IN ('RESOLVED','CLOSED') THEN :updatedAt ELSE resolved_at END
         WHERE id = :id
        """)
    Mono<Integer> updateStatus(Long id, String status, LocalDateTime updatedAt, String updatedBy);

    @Modifying
    @Query("""
        UPDATE public.incident
           SET status     = :status,
               severity   = :severity,
               title      = :title,
               description = :description,
               updated_at = :updatedAt,
               updated_by = :updatedBy,
               resolved_at = CASE WHEN :status IN ('RESOLVED','CLOSED') THEN :updatedAt ELSE resolved_at END
         WHERE id = :id
        """)
    Mono<Integer> updateIncident(Long id, String status, String severity,
                                 String title, String description,
                                 LocalDateTime updatedAt, String updatedBy);

    // ── Auto-resolve ───────────────────────────────────────────────────────────

    @Modifying
    @Query("""
        UPDATE public.incident
           SET status = 'RESOLVED', resolved_at = :resolvedAt, updated_at = :resolvedAt,
               updated_by = 'AUTO'
         WHERE trigger_key = :triggerKey AND status IN ('OPEN','INVESTIGATING')
        """)
    Mono<Integer> autoResolveByTriggerKey(String triggerKey, LocalDateTime resolvedAt);
}
