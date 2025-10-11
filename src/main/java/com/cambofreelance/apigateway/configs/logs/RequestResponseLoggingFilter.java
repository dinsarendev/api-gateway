package com.cambofreelance.apigateway.configs.logs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.info("Request: {} {} with headers: {}", request.getMethod(), request.getURI(), request.getHeaders());
        ServerHttpResponse response = exchange.getResponse();
        return chain.filter(exchange).doOnTerminate(() -> {
            log.info("Response status: {}", response.getStatusCode());
        });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
