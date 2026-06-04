package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.service.impl.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/management/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;
    private final AdminAuthHelper adminAuth;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.AUDIT_LOG_READ)
            .then(auditLogService.list(module, action, actor, result, from, to, page, size)
                .map(ResponseEntity::ok));
    }
}