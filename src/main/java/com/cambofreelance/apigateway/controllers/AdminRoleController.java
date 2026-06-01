package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.models.AdminRole;
import com.cambofreelance.apigateway.models.RolePermission;
import com.cambofreelance.apigateway.repositories.AdminPermissionRepository;
import com.cambofreelance.apigateway.repositories.AdminRoleRepository;
import com.cambofreelance.apigateway.repositories.RolePermissionRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleRepository       roleRepository;
    private final AdminPermissionRepository permissionRepository;
    private final RolePermissionRepository  rolePermissionRepository;

    // ── List permissions (for the create/edit modal) ──────────────────────────

    @GetMapping("/permissions")
    public Mono<ResponseEntity<List<PermissionDto>>> listPermissions() {
        return permissionRepository.findAllActive()
            .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
            .collectList()
            .map(ResponseEntity::ok);
    }

    // ── List roles (paginated + search + status) ──────────────────────────────

    @GetMapping
    public Mono<ResponseEntity<PagedResponse>> list(
            @RequestParam(defaultValue = "ACT") String status,
            @RequestParam(defaultValue = "")    String search,
            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "15")  int    size) {

        String statusParam = status.isBlank() ? null : status;
        String searchParam = search.isBlank() ? null : "%" + search + "%";
        long   offset      = (long) page * size;

        Mono<Long> countMono = roleRepository.countByFilter(statusParam, searchParam);
        Mono<List<RoleDto>> rowsMono = roleRepository
            .findByFilter(statusParam, searchParam, size, offset)
            .flatMap(r -> permissionRepository.findByRoleId(r.getId())
                .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
                .collectList()
                .map(perms -> toDto(r, perms)))
            .collectList();

        return Mono.zip(countMono, rowsMono)
            .map(t -> ResponseEntity.ok(new PagedResponse(t.getT2(), t.getT1(), page, size)));
    }

    // ── Get single ────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public Mono<ResponseEntity<RoleDto>> getById(@PathVariable Long id) {
        return roleRepository.findById(id)
            .flatMap(r -> permissionRepository.findByRoleId(r.getId())
                .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
                .collectList()
                .map(perms -> ResponseEntity.ok(toDto(r, perms))))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<RoleDto>> create(
            @RequestBody  RoleRequest   req,
            ServerWebExchange            exchange) {

        String actor = actor(exchange);
        AdminRole role = AdminRole.builder()
            .name(req.name())
            .description(req.description())
            .createdAt(LocalDateTime.now())
            .createdBy(actor)
            .build();

        return roleRepository.findActiveByName(req.name())
            .flatMap(existing -> Mono.<ResponseEntity<RoleDto>>just(
                ResponseEntity.status(HttpStatus.CONFLICT).build()))
            .switchIfEmpty(
                roleRepository.save(role)
                    .flatMap(saved -> assignPermissions(saved.getId(), req.permissionIds())
                        .then(permissionRepository.findByRoleId(saved.getId())
                            .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
                            .collectList()
                            .map(perms -> ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(toDto(saved, perms)))))
            );
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RoleDto>> update(
            @PathVariable Long        id,
            @RequestBody  RoleRequest req,
            ServerWebExchange          exchange) {

        String actor = actor(exchange);
        return roleRepository.findById(id)
            .flatMap(existing -> {
                existing.setDescription(req.description());
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setUpdatedBy(actor);
                return roleRepository.save(existing);
            })
            .flatMap(saved -> assignPermissions(saved.getId(), req.permissionIds())
                .then(permissionRepository.findByRoleId(saved.getId())
                    .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
                    .collectList()
                    .map(perms -> ResponseEntity.ok(toDto(saved, perms)))))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Status toggle ─────────────────────────────────────────────────────────

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Map<String, Object>>> updateStatus(
            @PathVariable Long          id,
            @RequestBody  StatusRequest req,
            ServerWebExchange            exchange) {

        String actor = actor(exchange);
        return roleRepository.findById(id)
            .flatMap(r -> roleRepository.updateStatus(id, req.status(), LocalDateTime.now(), actor))
            .map(rows -> ResponseEntity.ok(Map.<String, Object>of(
                "id", id, "status", req.status(), "message", "Role status updated")))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Void> assignPermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return rolePermissionRepository.deleteByRoleId(roleId).then();
        }
        return rolePermissionRepository.deleteByRoleId(roleId)
            .thenMany(permissionRepository.findAllById(permissionIds)
                .flatMap(p -> rolePermissionRepository.save(
                    RolePermission.builder().roleId(roleId).permissionId(p.getId()).build())))
            .then();
    }

    private String actor(ServerWebExchange exchange) {
        String v = exchange.getRequest().getHeaders().getFirst("X-Admin-User");
        return v != null ? v : "system";
    }

    private RoleDto toDto(AdminRole r, List<PermissionDto> perms) {
        return new RoleDto(r.getId(), r.getName(), r.getDescription(),
            r.getStatus(), r.getCreatedAt(), perms);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoleDto(
        Long             id,
        String           name,
        String           description,
        String           status,
        @JsonProperty("created_at") LocalDateTime createdAt,
        List<PermissionDto> permissions
    ) {}

    public record PermissionDto(Long id, String name, String description) {}

    public record PagedResponse(List<RoleDto> data, long total, int page, int size) {}

    public record RoleRequest(
        String     name,
        String     description,
        @JsonProperty("permission_ids") List<Long> permissionIds
    ) {}

    public record StatusRequest(String status) {}
}