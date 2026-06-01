package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.models.ApiGroupRoute;
import com.cambofreelance.apigateway.repositories.ApiGroupRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final ApiGroupRouteRepository groupRouteRepository;

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public Mono<ResponseEntity<List<ApiGroupRoute>>> list() {
        return groupRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiGroupRoute>> getById(@PathVariable Long id) {
        return groupRouteRepository.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<ApiGroupRoute>> create(@RequestBody ApiGroupRoute request) {
        return groupRouteRepository.existsByCode(request.getCode())
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    return Mono.<ResponseEntity<ApiGroupRoute>>just(
                        ResponseEntity.status(HttpStatus.CONFLICT).build());
                }
                request.setId(null);
                request.setStatus(Constants.STATUS_ACTIVE);
                request.setCreatedAt(LocalDateTime.now());
                request.setCreatedBy("admin");
                return groupRouteRepository.save(request)
                    .map(g -> ResponseEntity.status(HttpStatus.CREATED).body(g));
            });
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiGroupRoute>> update(
            @PathVariable Long id,
            @RequestBody ApiGroupRoute request) {
        return groupRouteRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Group not found: " + id)))
            .flatMap(existing -> {
                if (request.getUri()    != null) existing.setUri(request.getUri());
                if (request.getCode()   != null) existing.setCode(request.getCode());
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setUpdatedBy("admin");
                return groupRouteRepository.save(existing);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id) {
        return groupRouteRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Group not found: " + id)))
            .flatMap(g -> {
                g.setStatus("INACT");
                g.setUpdatedAt(LocalDateTime.now());
                g.setUpdatedBy("admin");
                return groupRouteRepository.save(g);
            })
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                "id", id, "message", "Service group deleted")));
    }
}