package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.models.AlertHistory;
import com.cambofreelance.apigateway.models.Incident;
import com.cambofreelance.apigateway.repositories.AlertHistoryRepository;
import com.cambofreelance.apigateway.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertNotificationService alertNotificationService;

    // ── CRUD ───────────────────────────────────────────────────────────────────

    public Mono<Incident> create(Incident request, String createdBy) {
        request.setId(null);
        request.setSource(Incident.SOURCE_MANUAL);
        request.setStatus(Incident.STATUS_OPEN);
        request.setOpenedAt(LocalDateTime.now());
        request.setCreatedBy(createdBy);
        if (request.getSeverity() == null) request.setSeverity(Incident.SEV_HIGH);
        return incidentRepository.save(request)
            .doOnSuccess(saved -> {
                recordHistory(saved.getId(), AlertHistory.ACTION_OPENED,
                    null, Incident.STATUS_OPEN, null, createdBy);
                alertNotificationService.notifyOpen(saved);
            });
    }

    /**
     * Creates an AUTO incident only if no open incident with the same trigger_key exists.
     * Returns empty Mono if deduplicated (incident already open).
     */
    public Mono<Incident> createAuto(Incident template) {
        if (template.getTriggerKey() == null) return incidentRepository.save(template);
        return incidentRepository.findOpenByTriggerKey(template.getTriggerKey())
            .hasElement()
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) return Mono.empty();
                template.setSource(Incident.SOURCE_AUTO);
                template.setStatus(Incident.STATUS_OPEN);
                template.setOpenedAt(LocalDateTime.now());
                template.setCreatedBy("AUTO");
                return incidentRepository.save(template)
                    .doOnSuccess(saved -> {
                        recordHistory(saved.getId(), AlertHistory.ACTION_OPENED,
                            null, Incident.STATUS_OPEN, null, "AUTO");
                        alertNotificationService.notifyOpen(saved);
                    });
            });
    }

    public Mono<Incident> acknowledge(Long id, String acknowledgedBy, String note) {
        LocalDateTime now = LocalDateTime.now();
        return incidentRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)))
            .flatMap(existing -> {
                String oldStatus = existing.getStatus();
                return incidentRepository.acknowledge(id, now, acknowledgedBy)
                    .filter(rows -> rows > 0)
                    .switchIfEmpty(Mono.error(new RuntimeException(
                        "Incident #" + id + " cannot be acknowledged — it is not OPEN")))
                    .flatMap(r -> incidentRepository.findById(id))
                    .doOnSuccess(updated -> {
                        recordHistory(id, AlertHistory.ACTION_ACKNOWLEDGED,
                            oldStatus, Incident.STATUS_INVESTIGATING, note, acknowledgedBy);
                        alertNotificationService.notifyAcknowledge(updated);
                    });
            });
    }

    public Mono<Incident> update(Long id, String status, String severity,
                                  String title, String description, String updatedBy) {
        return incidentRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)))
            .flatMap(existing -> {
                String oldStatus = existing.getStatus();
                return incidentRepository.updateIncident(
                        id, status, severity, title, description, LocalDateTime.now(), updatedBy)
                    .filter(rows -> rows > 0)
                    .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)))
                    .flatMap(r -> incidentRepository.findById(id))
                    .doOnSuccess(updated -> recordHistory(id, AlertHistory.ACTION_UPDATED,
                        oldStatus, updated.getStatus(), null, updatedBy));
            });
    }

    public Mono<Incident> resolve(Long id, String resolvedBy) {
        return incidentRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)))
            .flatMap(existing -> {
                String oldStatus = existing.getStatus();
                return incidentRepository.updateStatus(id, Incident.STATUS_RESOLVED, LocalDateTime.now(), resolvedBy)
                    .filter(rows -> rows > 0)
                    .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)))
                    .flatMap(r -> incidentRepository.findById(id))
                    .doOnSuccess(updated -> {
                        recordHistory(id, AlertHistory.ACTION_RESOLVED,
                            oldStatus, Incident.STATUS_RESOLVED, null, resolvedBy);
                        alertNotificationService.notifyResolve(updated);
                    });
            });
    }

    public Mono<Void> close(Long id, String updatedBy) {
        return incidentRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)))
            .flatMap(existing ->
                incidentRepository.updateStatus(id, Incident.STATUS_CLOSED, LocalDateTime.now(), updatedBy)
                    .doOnSuccess(rows -> {
                        if (rows > 0) recordHistory(id, AlertHistory.ACTION_CLOSED,
                            existing.getStatus(), Incident.STATUS_CLOSED, null, updatedBy);
                    })
            )
            .then();
    }

    public Mono<Incident> getById(Long id) {
        return incidentRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Incident not found: " + id)));
    }

    public Flux<Incident> list(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return incidentRepository.findAllByOrderByOpenedAtDesc();
        }
        return incidentRepository.findAllByStatusOrderByOpenedAtDesc(status);
    }

    public Mono<Integer> autoResolve(String triggerKey) {
        return incidentRepository.findOpenByTriggerKey(triggerKey)
            .flatMap(existing ->
                incidentRepository.autoResolveByTriggerKey(triggerKey, LocalDateTime.now())
                    .doOnSuccess(rows -> {
                        if (rows != null && rows > 0)
                            recordHistory(existing.getId(), AlertHistory.ACTION_RESOLVED,
                                existing.getStatus(), Incident.STATUS_RESOLVED, "Auto-resolved", "AUTO");
                    })
            )
            .switchIfEmpty(incidentRepository.autoResolveByTriggerKey(triggerKey, LocalDateTime.now()));
    }

    public Flux<AlertHistory> getHistory(Long incidentId) {
        return alertHistoryRepository.findAllByIncidentIdOrderByPerformedAtDesc(incidentId);
    }

    // ── Dashboard stats ────────────────────────────────────────────────────────

    public Mono<Map<String, Object>> getDashboard() {
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);

        Mono<Long> openCount     = incidentRepository.countByStatus(Incident.STATUS_OPEN);
        Mono<Long> invCount      = incidentRepository.countByStatus(Incident.STATUS_INVESTIGATING);
        Mono<Long> criticalCount = incidentRepository.countBySeverityAndStatusNot(
            Incident.SEV_CRITICAL, Incident.STATUS_RESOLVED);
        Mono<Long> resolved24h   = incidentRepository.countResolvedSince(since24h);
        Mono<Double> mttrMins    = incidentRepository.avgMttrMinutesSince(since30d);
        Mono<Double> mtbfMins    = computeMtbf(since30d);

        return Mono.zip(openCount, invCount, criticalCount, resolved24h, mttrMins, mtbfMins)
            .map(t -> {
                long open        = t.getT1() + t.getT2();
                long critical    = t.getT3();
                long resolved    = t.getT4();
                double mttr      = Math.round(t.getT5() * 10.0) / 10.0;
                double mtbf      = Math.round(t.getT6() * 10.0) / 10.0;

                Map<String, Object> dash = new LinkedHashMap<>();
                dash.put("open_incidents",       open);
                dash.put("investigating",         t.getT2());
                dash.put("critical_incidents",    critical);
                dash.put("resolved_last_24h",     resolved);
                dash.put("mttr_minutes",          mttr);
                dash.put("mttr_human",            formatDuration(mttr));
                dash.put("mtbf_minutes",          mtbf);
                dash.put("mtbf_human",            formatDuration(mtbf));
                return dash;
            });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * MTBF = average gap between consecutive incident openings over last 30 days.
     * Computed as: total window / incident count (simplified).
     */
    private Mono<Double> computeMtbf(LocalDateTime since) {
        return incidentRepository.findOpenedAtSince(since)
            .collectList()
            .map(timestamps -> {
                if (timestamps.size() < 2) return 0.0;
                // Sort ascending (query already sorted) and compute average gap
                double totalGapMinutes = 0;
                for (int i = 1; i < timestamps.size(); i++) {
                    long gapSec = java.time.Duration.between(timestamps.get(i - 1), timestamps.get(i)).toSeconds();
                    totalGapMinutes += gapSec / 60.0;
                }
                return totalGapMinutes / (timestamps.size() - 1);
            });
    }

    private String formatDuration(double minutes) {
        if (minutes <= 0) return "N/A";
        if (minutes < 60) return String.format("%.0f min", minutes);
        if (minutes < 1440) return String.format("%.1f hr", minutes / 60);
        return String.format("%.1f days", minutes / 1440);
    }

    private void recordHistory(Long incidentId, String action, String oldStatus,
                                String newStatus, String note, String performedBy) {
        AlertHistory entry = AlertHistory.builder()
            .incidentId(incidentId)
            .action(action)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .note(note)
            .performedBy(performedBy)
            .performedAt(LocalDateTime.now())
            .build();
        alertHistoryRepository.save(entry)
            .subscribe(
                saved -> {},
                e -> log.error("Failed to record alert history for incident #{}: {}", incidentId, e.getMessage())
            );
    }
}
