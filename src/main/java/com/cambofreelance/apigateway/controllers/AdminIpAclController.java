package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.caches.IpAclCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.models.IpAccessControl;
import com.cambofreelance.apigateway.repositories.IpAccessControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/ip-acl")
@RequiredArgsConstructor
public class AdminIpAclController {

    private final IpAccessControlRepository repository;

    @GetMapping
    public Mono<ResponseEntity<List<IpAccessControl>>> list() {
        return repository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<IpAccessControl>> create(@RequestBody IpAccessControl body) {
        body.setId(null);
        body.setStatus(Constants.STATUS_ACTIVE);
        body.setCreatedAt(LocalDateTime.now());
        body.setCreatedBy("admin");
        if (body.getScope() == null) body.setScope("GLOBAL");
        return repository.save(body)
            .doOnSuccess(r -> reloadCache())
            .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id) {
        return repository.findById(id)
            .flatMap(r -> {
                r.setStatus("INACT");
                r.setUpdatedAt(LocalDateTime.now());
                r.setUpdatedBy("admin");
                return repository.save(r);
            })
            .doOnSuccess(r -> reloadCache())
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "IP ACL rule deleted")));
    }

    private void reloadCache() {
        repository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectList()
            .subscribe(IpAclCache::init);
    }
}