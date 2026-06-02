package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.dto.RouteApiRequest;
import com.cambofreelance.apigateway.dto.RouteApiResponse;
import com.cambofreelance.apigateway.exception.RouteCreationException;
import com.cambofreelance.apigateway.exception.RouteNotFoundException;
import com.cambofreelance.apigateway.models.ApiRoute;
import com.cambofreelance.apigateway.registry.ApiMigrateRegistry;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
import com.cambofreelance.apigateway.service.ApiRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import io.r2dbc.spi.Parameters;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiRouteServiceImpl implements ApiRouteService {

    private static final String ADMIN = "admin";

    private final ApiRouteRepository apiRouteRepository;
    private final GatewayRouteService gatewayRouteService;
    private final ApiMigrateRegistry apiMigrateRegistry;
    private final DatabaseClient databaseClient;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Inserts via raw SQL to avoid writing the JOIN-derived `uri` column,
     * then re-fetches with the JOIN so the response includes the resolved URI.
     */
    @Override
    public Mono<RouteApiResponse> create(RouteApiRequest req) {
        return databaseClient.sql("""
                INSERT INTO api_route
                    (group_code, path, method, description, application_id,
                     is_public, is_encrypt, enable_circuit_breaker,
                     rate_limit, rate_limit_duration, priority,
                     start_time, end_time,
                     auth_type, required_roles, required_permissions,
                     api_type, version, deprecated, sunset_date,
                     status, created_at, created_by)
                VALUES
                    (:groupCode, :path, :method, :description, :applicationId,
                     :isPublic, :isEncrypt, :enableCircuitBreaker,
                     :rateLimit, :rateLimitDuration, :priority,
                     :startTime, :endTime,
                     :authType, :requiredRoles, :requiredPermissions,
                     :apiType, :version, :deprecated, :sunsetDate,
                     'ACT', NOW(), :createdBy)
                RETURNING id
                """)
            .bind("groupCode",            orEmpty(req.groupCode()))
            .bind("path",                 orEmpty(req.path()))
            .bind("method",               orEmpty(req.method()).toUpperCase())
            .bind("description",          orEmpty(req.description()))
            .bind("applicationId",        orEmpty(req.applicationId()))
            .bind("isPublic",             orEmpty(req.isPublic(), "N"))
            .bind("isEncrypt",            orEmpty(req.isEncrypt(), "N"))
            .bind("enableCircuitBreaker", orEmpty(req.enableCircuitBreaker(), "N"))
            .bind("rateLimit",            req.rateLimit() != null ? req.rateLimit() : Parameters.in(Integer.class))
            .bind("rateLimitDuration",    req.rateLimitDuration() != null ? req.rateLimitDuration() : Parameters.in(Integer.class))
            .bind("priority",             req.priority() != null ? req.priority() : 1)
            .bind("startTime",            req.startTime() != null ? req.startTime() : Parameters.in(LocalDateTime.class))
            .bind("endTime",              req.endTime() != null ? req.endTime() : Parameters.in(LocalDateTime.class))
            .bind("authType",             req.authType() != null ? req.authType() : "JWT")
            .bind("requiredRoles",        req.requiredRoles() != null ? req.requiredRoles() : Parameters.in(String.class))
            .bind("requiredPermissions",  req.requiredPermissions() != null ? req.requiredPermissions() : Parameters.in(String.class))
            .bind("apiType",              req.apiType() != null ? req.apiType().toUpperCase() : Constants.API_TYPE_REST)
            .bind("version",              req.version() != null ? req.version() : Parameters.in(String.class))
            .bind("deprecated",           orEmpty(req.deprecated(), "N"))
            .bind("sunsetDate",           req.sunsetDate() != null ? req.sunsetDate() : Parameters.in(LocalDateTime.class))
            .bind("createdBy",            ADMIN)
            .map(row -> row.get("id", Long.class))
            .first()
            .flatMap(apiRouteRepository::findByIdWithUri)
            .map(this::toResponse)
            .doOnSuccess(r -> refreshAll())
            .onErrorMap(e -> {
                log.error("Failed to create route: {}", e.getMessage());
                return new RouteCreationException("Failed to create route: " + e.getMessage());
            });
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public Mono<RouteApiResponse> update(Long id, RouteApiRequest req) {
        return apiRouteRepository.findByIdWithUri(id)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("Route not found: " + id)))
            .flatMap(existing -> apiRouteRepository.updateRoute(
                id,
                orEmpty(req.groupCode(), existing.getGroupCode()),
                orEmpty(req.path(),        existing.getPath()),
                orEmpty(req.method(),      existing.getMethod()).toUpperCase(),
                req.description()          != null ? req.description()          : existing.getDescription(),
                req.applicationId()        != null ? req.applicationId()        : existing.getApplicationId(),
                orEmpty(req.isPublic(),             existing.getIsPublic()),
                orEmpty(req.isEncrypt(),            existing.getIsEncrypt()),
                orEmpty(req.enableCircuitBreaker(), existing.getEnableCircuitBreaker()),
                req.rateLimit()            != null ? req.rateLimit()            : existing.getRateLimit(),
                req.rateLimitDuration()    != null ? req.rateLimitDuration()    : existing.getRateLimitDuration(),
                req.priority()             != null ? req.priority()             : existing.getPriority(),
                req.startTime()            != null ? req.startTime()            : existing.getStartTime(),
                req.endTime()              != null ? req.endTime()              : existing.getEndTime(),
                req.authType()             != null ? req.authType()             : existing.getAuthType(),
                req.requiredRoles()        != null ? req.requiredRoles()        : existing.getRequiredRoles(),
                req.requiredPermissions()  != null ? req.requiredPermissions()  : existing.getRequiredPermissions(),
                req.apiType()              != null ? req.apiType().toUpperCase() : existing.getApiType(),
                req.version()              != null ? req.version()              : existing.getVersion(),
                orEmpty(req.deprecated(),           existing.getDeprecated()),
                req.sunsetDate()           != null ? req.sunsetDate()           : existing.getSunsetDate(),
                LocalDateTime.now(), ADMIN
            ))
            .filter(rows -> rows > 0)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("Route not found: " + id)))
            .flatMap(r -> apiRouteRepository.findByIdWithUri(id))
            .map(this::toResponse)
            .doOnSuccess(r -> refreshAll());
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    public Flux<RouteApiResponse> findAll() {
        return apiRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE).map(this::toResponse);
    }

    @Override
    public Flux<RouteApiResponse> findAllByStatus(String status) {
        return apiRouteRepository.findAllByStatus(status).map(this::toResponse);
    }

    @Override
    public Mono<RouteApiResponse> findById(Long id) {
        return apiRouteRepository.findByIdWithUri(id)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("Route not found: " + id)))
            .map(this::toResponse);
    }

    // ── Status changes ────────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(Long id) {
        return apiRouteRepository.findByIdWithUri(id)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("Route not found: " + id)))
            .flatMap(r -> apiRouteRepository.updateStatus(id, "INACT", LocalDateTime.now(), ADMIN))
            .doOnSuccess(r -> refreshAll())
            .then();
    }

    @Override
    public Mono<Void> enable(Long id) {
        return apiRouteRepository.updateStatus(id, Constants.STATUS_ACTIVE, LocalDateTime.now(), ADMIN)
            .filter(rows -> rows > 0)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("Route not found: " + id)))
            .doOnSuccess(r -> refreshAll())
            .then();
    }

    @Override
    public Mono<Void> disable(Long id) {
        return apiRouteRepository.updateStatus(id, "INACT", LocalDateTime.now(), ADMIN)
            .filter(rows -> rows > 0)
            .switchIfEmpty(Mono.error(new RouteNotFoundException("Route not found: " + id)))
            .doOnSuccess(r -> refreshAll())
            .then();
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> reloadRoutes() {
        return Mono.fromRunnable(this::refreshAll);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshAll() {
        Mono.fromRunnable(() -> {
            try {
                gatewayRouteService.refreshRoutes();
                apiMigrateRegistry.loadComponent();
            } catch (Exception e) {
                log.error("Route refresh failed: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private RouteApiResponse toResponse(ApiRoute r) {
        return new RouteApiResponse(
            r.getId(), r.getUri(), r.getGroupCode(),
            r.getPath(), r.getMethod(), r.getDescription(), r.getApplicationId(),
            r.getIsPublic(), r.getIsEncrypt(), r.getEnableCircuitBreaker(),
            r.getRateLimit(), r.getRateLimitDuration(), r.getPriority(),
            r.getStartTime(), r.getEndTime(),
            r.getStatus(), r.getCreatedBy(), r.getCreatedAt(),
            r.getUpdatedBy(), r.getUpdatedAt(),
            r.getAuthType(), r.getRequiredRoles(), r.getRequiredPermissions(),
            r.getApiType(),
            r.getVersion(), r.getDeprecated(), r.getSunsetDate()
        );
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private String orEmpty(String value, String fallback) {
        return value != null ? value : (fallback != null ? fallback : "");
    }
}
