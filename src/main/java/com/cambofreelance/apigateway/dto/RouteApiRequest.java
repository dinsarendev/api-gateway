package com.cambofreelance.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RouteApiRequest(Long id,
                              String uri,
                              String path,
                              String method,
                              String description,
                              @JsonProperty("application_id") String applicationId,
                              @JsonProperty("rate_limit") Integer rateLimit,
                              @JsonProperty("rate_limit_duration") Integer rateLimitDuration,
                              String status, String isPublic) {

}
