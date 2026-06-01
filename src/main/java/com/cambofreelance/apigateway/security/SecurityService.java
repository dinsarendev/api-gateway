package com.cambofreelance.apigateway.security;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import com.cambofreelance.apigateway.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

    private final JwtUtils jwtUtils;
    private final ApiKeyService apiKeyService;
    private final OAuth2TokenService oauth2TokenService;

    /**
     * Authenticates the request based on the route's auth_type.
     * Returns a SecurityPrincipal on success, or an error Mono on failure.
     */
    public Mono<SecurityPrincipal> authenticate(ServerWebExchange exchange, ApiRouteDto route) {
        String authType = StringUtils.defaultIfBlank(route.getAuthType(), "JWT").toUpperCase();
        return switch (authType) {
            case "OAUTH2"   -> authenticateOAuth2(exchange);
            case "API_KEY"  -> authenticateApiKey(exchange);
            case "NONE"     -> Mono.just(SecurityPrincipal.anonymous());
            default         -> Mono.defer(() -> authenticateJwt(exchange));
        };
    }

    private Mono<SecurityPrincipal> authenticateJwt(ServerWebExchange exchange) {
        String token = extractBearer(exchange);
        if (token == null) return authError("Missing or invalid Authorization header");
        if (!jwtUtils.validateJwtToken(token)) return authError("Invalid or expired JWT token");
        return Mono.just(SecurityPrincipal.of(
            jwtUtils.getUserIdFromJwtToken(token),
            jwtUtils.getRolesFromToken(token),
            jwtUtils.getPermissionsFromToken(token),
            "JWT"
        ));
    }

    private Mono<SecurityPrincipal> authenticateOAuth2(ServerWebExchange exchange) {
        String token = extractBearer(exchange);
        if (token == null) return authError("Missing or invalid Authorization header");
        return oauth2TokenService.introspect(token);
    }

    private Mono<SecurityPrincipal> authenticateApiKey(ServerWebExchange exchange) {
        String key = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        if (StringUtils.isBlank(key)) return authError("Missing X-Api-Key header");
        return apiKeyService.validate(key)
            .switchIfEmpty(authError("Invalid or revoked API key"));
    }

    /**
     * Verifies required_roles: ALL listed roles must be present in the principal.
     */
    public Mono<Void> checkRoles(SecurityPrincipal principal, ApiRouteDto route) {
        String required = route.getRequiredRoles();
        if (StringUtils.isBlank(required)) return Mono.empty();
        for (String role : required.split(",")) {
            if (!principal.roles().contains(role.trim())) {
                return Mono.error(new ForbiddenException("Insufficient roles — requires: " + role.trim()));
            }
        }
        return Mono.empty();
    }

    /**
     * Verifies required_permissions: ALL listed permissions must be present in the principal.
     */
    public Mono<Void> checkPermissions(SecurityPrincipal principal, ApiRouteDto route) {
        String required = route.getRequiredPermissions();
        if (StringUtils.isBlank(required)) return Mono.empty();
        for (String perm : required.split(",")) {
            if (!principal.permissions().contains(perm.trim())) {
                return Mono.error(new ForbiddenException("Insufficient permissions — requires: " + perm.trim()));
            }
        }
        return Mono.empty();
    }

    private String extractBearer(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(header) || !header.startsWith(Constants.BEARER)) return null;
        return header.substring(Constants.BEARER.length());
    }

    private <T> Mono<T> authError(String message) {
        return Mono.error(new UnauthorizedException(message));
    }

    // ── Checked exceptions used as signal types ───────────────────────────────

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String msg) { super(msg); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String msg) { super(msg); }
    }
}