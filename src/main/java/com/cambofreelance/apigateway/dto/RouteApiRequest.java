package com.cambofreelance.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record RouteApiRequest(
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
    @JsonProperty("end_time")              LocalDateTime endTime
) {}

