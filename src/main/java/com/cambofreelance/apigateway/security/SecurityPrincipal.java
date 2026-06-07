package com.cambofreelance.apigateway.security;

import java.util.List;

public record SecurityPrincipal(
    String userId,
    List<String> roles,
    List<String> permissions,
    String authType
) {
    public static SecurityPrincipal anonymous() {
        return new SecurityPrincipal("anonymous", List.of(), List.of(), "NONE");
    }

    public static SecurityPrincipal of(String userId, List<String> roles, List<String> permissions, String authType) {
        return new SecurityPrincipal(
            userId != null ? userId : "unknown",
            roles != null ? roles : List.of(),
            permissions != null ? permissions : List.of(),
            authType
        );
    }
}