package com.cambofreelance.apigateway.configs;

import com.cambofreelance.apigateway.models.AuditLog;
import com.cambofreelance.apigateway.repositories.*;
import com.cambofreelance.apigateway.service.impl.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(-100)
@RequiredArgsConstructor
public class AuditLoggingWebFilter implements WebFilter {

    public static final String ATTR_OLD_VALUE = "auditOldValue";
    public static final String ATTR_NEW_VALUE = "auditNewValue";

    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("/admin/[^/]+/(\\d+)");
    private static final Set<String> AUDITED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> BODY_METHODS    = Set.of("POST", "PUT", "PATCH");

    /** Field-name keywords (lower-case) whose values are always masked in audit JSON. */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "passwd",
        "pin",
        "secret",
        "token",
        "keyhash", "key_hash",
        "passwordhash", "password_hash",
        "tokenhash", "token_hash",
        "clientsecret", "client_secret",
        "apikey", "api_key",
        "cvv", "ssn", "creditcard", "credit_card"
    );

    private final AuditLogService            auditLogService;
    private final ObjectMapper               objectMapper;
    private final ApiRouteRepository         apiRouteRepository;
    private final ApiGroupRouteRepository    apiGroupRouteRepository;
    private final AdminUserRepository        adminUserRepository;
    private final AdminRoleRepository        adminRoleRepository;
    private final ApiKeyRepository           apiKeyRepository;
    private final IpAccessControlRepository  ipAccessControlRepository;
    private final IncidentRepository         incidentRepository;
    private final OAuth2ProviderRepository   oauth2ProviderRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith("/admin/") || path.startsWith("/admin/audit-logs")) {
            return chain.filter(exchange);
        }

        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null || !AUDITED_METHODS.contains(method.name())) {
            return chain.filter(exchange);
        }

        String module   = resolveModule(path);
        String entityId = extractEntityId(path);

        // Load old entity value BEFORE mutation (for UPDATE / DELETE on a known entity)
        boolean needsOldValue = entityId != null
            && (HttpMethod.DELETE.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.PATCH.equals(method));

        Mono<Void> execute = BODY_METHODS.contains(method.name())
            ? cacheBodyAndChain(exchange, chain, path)
            : chain.filter(exchange).doFinally(signal -> safeRecord(exchange, path));

        if (needsOldValue) {
            return loadOldValueJson(module, entityId)
                .doOnNext(json -> exchange.getAttributes().put(ATTR_OLD_VALUE, json))
                .onErrorResume(e -> Mono.empty())
                .then(execute);
        }

        return execute;
    }

    // ── Body caching ────────────────────────────────────────────────────────────

    private Mono<Void> cacheBodyAndChain(ServerWebExchange exchange, WebFilterChain chain, String path) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
            .flatMap(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);

                String body = new String(bytes, StandardCharsets.UTF_8).trim();
                if (!body.isBlank() && exchange.getAttribute(ATTR_NEW_VALUE) == null) {
                    exchange.getAttributes().put(ATTR_NEW_VALUE, maskSensitiveFields(body));
                }

                // Wrap so downstream controllers can still read the body
                ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                        return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                    }
                };
                ServerWebExchange mutated = exchange.mutate().request(mutatedRequest).build();

                return chain.filter(mutated).doFinally(signal -> safeRecord(mutated, path));
            })
            // No body (empty stream) — proceed normally
            .switchIfEmpty(chain.filter(exchange).doFinally(signal -> safeRecord(exchange, path)));
    }

    // ── Old value loading ────────────────────────────────────────────────────────

    private Mono<String> loadOldValueJson(String module, String entityId) {
        try {
            long id = Long.parseLong(entityId);
            Mono<?> entity = switch (module) {
                case "ROUTE"    -> apiRouteRepository.findById(id);
                case "GROUP"    -> apiGroupRouteRepository.findById(id);
                case "USER"     -> adminUserRepository.findById(id);
                case "ROLE"     -> adminRoleRepository.findById(id);
                case "API_KEY"  -> apiKeyRepository.findById(id);
                case "IP_ACL"   -> ipAccessControlRepository.findById(id);
                case "INCIDENT" -> incidentRepository.findById(id);
                case "OAUTH2"   -> oauth2ProviderRepository.findById(id);
                default         -> Mono.empty();
            };
            return entity.flatMap(e -> {
                try {
                    return Mono.just(maskSensitiveFields(objectMapper.writeValueAsString(e)));
                } catch (Exception ex) {
                    return Mono.empty();
                }
            });
        } catch (NumberFormatException e) {
            return Mono.empty();
        }
    }

    // ── Record ───────────────────────────────────────────────────────────────────

    private void safeRecord(ServerWebExchange exchange, String path) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            HttpMethod method = request.getMethod();
            HttpStatusCode statusCode = exchange.getResponse().getStatusCode();

            String actor = exchange.getAttribute("adminUser");
            if (actor == null) actor = "anonymous";

            Long userId = exchange.getAttribute("adminUserId");

            boolean success = statusCode != null && statusCode.is2xxSuccessful();

            AuditLog entry = AuditLog.builder()
                .userId(userId)
                .actor(actor)
                .module(resolveModule(path))
                .action(resolveAction(method))
                .entityId(extractEntityId(path))
                .oldValue(exchange.getAttribute(ATTR_OLD_VALUE))
                .newValue(exchange.getAttribute(ATTR_NEW_VALUE))
                .method(method != null ? method.name() : "UNKNOWN")
                .path(path)
                .statusCode(statusCode != null ? statusCode.value() : 0)
                .result(success ? "SUCCESS" : "FAILURE")
                .ipAddress(resolveIp(request))
                .createdAt(LocalDateTime.now())
                .build();

            auditLogService.saveAsync(entry);
        } catch (Exception ignored) {
            // never fail the request due to audit logging
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String resolveModule(String path) {
        String[] segments = path.substring("/admin/".length()).split("/");
        if (segments.length == 0 || segments[0].isBlank()) return "ADMIN";
        return switch (segments[0]) {
            case "routes"           -> "ROUTE";
            case "groups"           -> "GROUP";
            case "users"            -> "USER";
            case "roles"            -> "ROLE";
            case "api-keys"         -> "API_KEY";
            case "ip-acl"           -> "IP_ACL";
            case "incidents"        -> "INCIDENT";
            case "oauth2-providers" -> "OAUTH2";
            case "auth"             -> "AUTH";
            case "dashboard"        -> "DASHBOARD";
            case "metrics"          -> "METRICS";
            default                 -> segments[0].toUpperCase().replace("-", "_");
        };
    }

    private String resolveAction(HttpMethod method) {
        if (method == null) return "UNKNOWN";
        return switch (method.name()) {
            case "POST"         -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE"       -> "DELETE";
            default             -> method.name();
        };
    }

    private String extractEntityId(String path) {
        Matcher m = ENTITY_ID_PATTERN.matcher(path);
        return m.find() ? m.group(1) : null;
    }

    private String resolveIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = request.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : null;
    }

    // ── Sensitive field masking ───────────────────────────────────────────────────

    private String maskSensitiveFields(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                maskNode((ObjectNode) node);
                return objectMapper.writeValueAsString(node);
            }
        } catch (Exception ignored) {}
        return json;
    }

    private void maskNode(ObjectNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey().toLowerCase().replace("_", "");
            if (isSensitiveKey(key)) {
                node.put(entry.getKey(), "***");
            } else if (entry.getValue().isObject()) {
                maskNode((ObjectNode) entry.getValue());
            } else if (entry.getValue().isArray()) {
                entry.getValue().forEach(child -> {
                    if (child.isObject()) maskNode((ObjectNode) child);
                });
            }
        }
    }

    private boolean isSensitiveKey(String normalizedKey) {
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (normalizedKey.contains(keyword)) return true;
        }
        return false;
    }
}