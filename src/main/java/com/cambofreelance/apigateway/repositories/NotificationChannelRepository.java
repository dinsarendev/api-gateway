package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.NotificationChannel;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface NotificationChannelRepository extends R2dbcRepository<NotificationChannel, Long> {

    Flux<NotificationChannel> findAllByStatus(String status);

    Flux<NotificationChannel> findAllByEnabledTrueAndStatus(String status);

    @Modifying
    @Query("""
        UPDATE public.notification_channel
           SET name = :name, type = :type, config = :config, enabled = :enabled,
               min_severity = :minSeverity, on_open = :onOpen,
               on_acknowledge = :onAcknowledge, on_resolve = :onResolve,
               updated_at = :updatedAt, updated_by = :updatedBy
         WHERE id = :id
        """)
    Mono<Integer> updateChannel(Long id, String name, String type, String config,
                                boolean enabled, String minSeverity,
                                boolean onOpen, boolean onAcknowledge, boolean onResolve,
                                LocalDateTime updatedAt, String updatedBy);

    @Modifying
    @Query("UPDATE public.notification_channel SET status = 'INACT', updated_at = :updatedAt, updated_by = :updatedBy WHERE id = :id")
    Mono<Integer> softDelete(Long id, LocalDateTime updatedAt, String updatedBy);
}
