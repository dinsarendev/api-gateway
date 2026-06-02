package com.cambofreelance.apigateway.configs;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive helper for permission-based pre-authorization of admin endpoints.
 * SUPER_ADMIN role bypasses all permission checks.
 */
@Component
public class AdminAuthHelper {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Returns empty Mono if the caller has the required permission (or is SUPER_ADMIN),
     * otherwise errors with 403 Forbidden.
     */
    public Mono<Void> require(ServerWebExchange exchange, String permission) {
        if (isSuperAdmin(exchange)) return Mono.empty();
        List<String> perms = exchange.getAttribute("adminPermissions");
        if (perms != null && perms.stream().anyMatch(p -> p.equalsIgnoreCase(permission))) {
            return Mono.empty();
        }
        return Mono.error(new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Permission required: " + permission));
    }

    /**
     * Returns empty Mono if the caller has ANY of the listed permissions (or is SUPER_ADMIN).
     */
    public Mono<Void> requireAny(ServerWebExchange exchange, String... permissions) {
        if (isSuperAdmin(exchange)) return Mono.empty();
        List<String> perms = exchange.getAttribute("adminPermissions");
        if (perms != null) {
            for (String permission : permissions) {
                if (perms.stream().anyMatch(p -> p.equalsIgnoreCase(permission))) {
                    return Mono.empty();
                }
            }
        }
        return Mono.error(new ResponseStatusException(
            HttpStatus.FORBIDDEN, "One of these permissions is required: " + String.join(", ", permissions)));
    }

    public String currentUser(ServerWebExchange exchange) {
        String user = exchange.getAttribute("adminUser");
        return user != null ? user : "admin";
    }

    public boolean isSuperAdmin(ServerWebExchange exchange) {
        List<String> roles = exchange.getAttribute("adminRoles");
        return roles != null && roles.stream().anyMatch(SUPER_ADMIN::equalsIgnoreCase);
    }
}
