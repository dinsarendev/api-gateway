package com.cambofreelance.apigateway.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
public class AdminIndexController {

    /** Redirect bare root to the admin SPA. */
    @GetMapping("/")
    public Mono<Void> root(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create("/index.html"));
        return exchange.getResponse().setComplete();
    }
}