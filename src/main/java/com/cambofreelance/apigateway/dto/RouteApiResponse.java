package com.cambofreelance.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record RouteApiResponse(
    Long                                   id,
    String                                 uri,
    @JsonProperty("group_code")            String groupCode,
    String                                 path,
    String                                 method,
    String                                 description,
    @JsonProperty("application_id")        String applicationId,
    @JsonProperty("is_public")             String isPublic,
    @JsonProperty("is_encrypt")            String isEncrypt,
    @JsonProperty("enable_circuit_breaker") String enableCircuitBreaker,
    @JsonProperty("rate_limit")            Integer rateLimit,
    @JsonProperty("rate_limit_duration")   Integer rateLimitDuration,
    Integer                                priority,
    @JsonProperty("start_time")            LocalDateTime startTime,
    @JsonProperty("end_time")              LocalDateTime endTime,
    String                                 status,
    @JsonProperty("created_by")            String createdBy,
    @JsonProperty("created_at")            LocalDateTime createdAt,
    @JsonProperty("updated_by")            String updatedBy,
    @JsonProperty("updated_at")            LocalDateTime updatedAt,
    @JsonProperty("auth_type")             String authType,
    @JsonProperty("required_roles")        String requiredRoles,
    @JsonProperty("required_permissions")  String requiredPermissions,
    @JsonProperty("api_type")              String apiType,
    String                                 version,
    String                                 deprecated,
    @JsonProperty("sunset_date")           LocalDateTime sunsetDate
) {}

