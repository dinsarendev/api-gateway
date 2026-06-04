package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.models.OAuth2Provider;
import com.cambofreelance.apigateway.repositories.OAuth2ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management/admin/oauth2-providers")
@RequiredArgsConstructor
public class AdminOAuth2ProviderController {

    private final OAuth2ProviderRepository repository;
    private final AdminAuthHelper adminAuth;

    @GetMapping
    public Mono<ResponseEntity<List<OAuth2Provider>>> list(ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.SECURITY_READ)
            .then(repository.findAllByStatus(Constants.STATUS_ACTIVE)
                .collectList()
                .map(ResponseEntity::ok));
    }

    @PostMapping
    public Mono<ResponseEntity<OAuth2Provider>> create(@RequestBody OAuth2Provider body, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.SECURITY_WRITE)
            .then(Mono.fromSupplier(() -> {
                body.setId(null);
                body.setStatus(Constants.STATUS_ACTIVE);
                body.setCreatedAt(LocalDateTime.now());
                body.setCreatedBy(adminAuth.currentUser(exchange));
                return body;
            }).flatMap(repository::save)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r)));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<OAuth2Provider>> update(
            @PathVariable Long id, @RequestBody OAuth2Provider body, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.SECURITY_WRITE)
            .then(repository.findById(id)
                .flatMap(existing -> {
                    if (body.getName()             != null) existing.setName(body.getName());
                    if (body.getIntrospectionUri() != null) existing.setIntrospectionUri(body.getIntrospectionUri());
                    if (body.getClientId()         != null) existing.setClientId(body.getClientId());
                    if (body.getClientSecret()     != null) existing.setClientSecret(body.getClientSecret());
                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.setUpdatedBy(adminAuth.currentUser(exchange));
                    return repository.save(existing);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<OAuth2Provider>notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.SECURITY_WRITE)
            .then(repository.findById(id)
                .flatMap(p -> {
                    p.setStatus("INACT");
                    p.setUpdatedAt(LocalDateTime.now());
                    p.setUpdatedBy(adminAuth.currentUser(exchange));
                    return repository.save(p);
                })
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "Provider deleted"))));
    }
}
