package com.cambofreelance.apigateway.controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AdminIndexController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    /**
     * Serve index.html for every admin SPA route so BrowserRouter direct-URL
     * access works. Paths are enumerated explicitly to avoid intercepting
     * gateway proxy traffic.
     */
    @GetMapping(value = {
        "/", "/dashboard",
        "/routes", "/groups", "/registry", "/health",
        "/incidents", "/monitoring",
        "/security/api-keys", "/security/ip-acl", "/security/oauth2",
        "/users", "/roles", "/profile",
        "/audit-logs",
    }, produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> spa() {
        return Mono.just(INDEX);
    }
}