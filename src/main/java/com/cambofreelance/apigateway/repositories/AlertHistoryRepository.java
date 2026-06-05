package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AlertHistory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Repository
public interface AlertHistoryRepository extends R2dbcRepository<AlertHistory, Long> {

    Flux<AlertHistory> findAllByIncidentIdOrderByPerformedAtDesc(Long incidentId);

    @Query("SELECT * FROM public.alert_history WHERE performed_at >= :since ORDER BY performed_at DESC")
    Flux<AlertHistory> findSince(LocalDateTime since);
}
