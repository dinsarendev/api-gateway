package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.models.ApiKey;
import com.cambofreelance.apigateway.repositories.ApiKeyRepository;
import com.cambofreelance.apigateway.security.ApiKeyService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private final ApiKeyRepository repository;
    private final ApiKeyService apiKeyService;

    @GetMapping
    public Mono<ResponseEntity<List<ApiKey>>> list() {
        return repository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .map(ResponseEntity::ok);
    }

    /** Creates a key and returns the raw key ONCE in the response. */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(@RequestBody CreateKeyRequest req) {
        LocalDateTime expiresAt = req.expiresAt() != null ? LocalDateTime.parse(req.expiresAt()) : null;
        return apiKeyService.createKey(req.name(), req.clientId(), req.roles(), req.permissions(), expiresAt)
            .map(rawKey -> ResponseEntity.status(HttpStatus.CREATED).body(
                Map.<String, Object>of(
                    "message", "API key created — copy the key now, it will not be shown again",
                    "key", rawKey
                )
            ));
    }

    /** Revokes (soft-deletes) an API key. */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> revoke(@PathVariable Long id) {
        return repository.findById(id)
            .flatMap(k -> {
                k.setStatus("INACT");
                k.setUpdatedAt(LocalDateTime.now());
                k.setUpdatedBy("admin");
                return repository.save(k);
            })
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "API key revoked")));
    }

    public record CreateKeyRequest(
        String name,
        @JsonProperty("client_id")  String clientId,
        String roles,
        String permissions,
        @JsonProperty("expires_at") String expiresAt
    ) {}
}