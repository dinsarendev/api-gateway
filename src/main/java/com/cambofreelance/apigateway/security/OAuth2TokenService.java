package com.cambofreelance.apigateway.security;

import com.cambofreelance.apigateway.repositories.OAuth2ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final OAuth2ProviderRepository providerRepository;
    private final WebClient.Builder webClientBuilder;

    /** Introspects the Bearer token against the first active OAuth2 provider. */
    public Mono<SecurityPrincipal> introspect(String token) {
        return providerRepository.findFirstActive()
            .flatMap(provider -> webClientBuilder.build()
                .post()
                .uri(provider.getIntrospectionUri())
                .headers(h -> h.setBasicAuth(provider.getClientId(), provider.getClientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("token", token))
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    Object active = body.get("active");
                    if (!Boolean.TRUE.equals(active)) return (SecurityPrincipal) null;
                    String sub         = str(body.get("sub"));
                    List<String> roles = extractList(body, "roles", "authorities", "groups");
                    List<String> perms = extractScope(body);
                    return SecurityPrincipal.of(sub, roles, perms, "OAUTH2");
                })
            )
            .filter(p -> p != null)
            .switchIfEmpty(Mono.error(new SecurityException("OAuth2 token inactive or provider unavailable")))
            .onErrorMap(e -> {
                log.warn("OAuth2 introspection failed: {}", e.getMessage());
                return new SecurityException("OAuth2 token validation failed");
            });
    }

    @SuppressWarnings("unchecked")
    private List<String> extractList(Map<?, ?> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val instanceof List<?> list)   return (List<String>) list;
            if (val instanceof String s && !s.isBlank())
                return Arrays.stream(s.split(",")).map(String::trim).toList();
        }
        return List.of();
    }

    private List<String> extractScope(Map<?, ?> body) {
        Object scope = body.get("scope");
        if (scope instanceof String s && !s.isBlank())
            return Arrays.stream(s.split(" ")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        return List.of();
    }

    private String str(Object val) {
        return val != null ? val.toString() : "unknown";
    }
}