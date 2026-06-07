package com.cambofreelance.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record DeprecateRouteRequest(
    @JsonProperty("sunset_date") LocalDateTime sunsetDate
) {}
