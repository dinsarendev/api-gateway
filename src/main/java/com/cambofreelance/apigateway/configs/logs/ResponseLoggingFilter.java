package com.cambofreelance.apigateway.configs.logs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ResponseLoggingFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            log.info("Response Status Code: {}", response.getStatusCode());
            log.info("Response Headers: {}", response.getHeaders());
        }));
    }
}
