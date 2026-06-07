package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.models.AuditLog;
import com.cambofreelance.apigateway.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void saveAsync(AuditLog entry) {
        auditLogRepository.save(entry)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                saved -> {},
                err -> log.warn("Audit log save failed: {}", err.getMessage())
            );
    }

    public Mono<Map<String, Object>> list(String module, String action, String actor,
                                          String result, LocalDateTime from, LocalDateTime to,
                                          int page, int size) {
        String moduleParam = (module == null || module.isBlank()) ? null : module.toUpperCase();
        String actionParam = (action == null || action.isBlank()) ? null : action.toUpperCase();
        String actorParam  = (actor  == null || actor.isBlank())  ? null : "%" + actor + "%";
        String resultParam = (result == null || result.isBlank()) ? null : result.toUpperCase();
        long offset = (long) page * size;

        Mono<Long> countMono = auditLogRepository.countByFilter(
            moduleParam, actionParam, actorParam, resultParam, from, to);
        Mono<List<AuditLog>> rowsMono = auditLogRepository
            .findByFilter(moduleParam, actionParam, actorParam, resultParam, from, to, size, offset)
            .collectList();

        return Mono.zip(countMono, rowsMono).map(t -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("data",  t.getT2());
            resp.put("total", t.getT1());
            resp.put("page",  page);
            resp.put("size",  size);
            return resp;
        });
    }
}