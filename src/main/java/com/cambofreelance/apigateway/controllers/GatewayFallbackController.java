package com.cambofreelance.apigateway.controllers;

import io.micrometer.tracing.Tracer;
import java.util.Collections;
import java.util.Objects;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.ErrorCode;
import com.cambofreelance.apigateway.exception.MessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    private final Tracer tracer;

    public GatewayFallbackController(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Handles all HTTP methods — the circuit breaker's forward: preserves the
     * original request method, so GET/POST/PUT/DELETE must all be accepted here.
     */
    @RequestMapping("/{group}")
    public Mono<ResponseEntity<MessageResponse>> fallback(
            @PathVariable String group,
            ServerWebExchange exchange) {

        String traceId = tracer.currentSpan() != null
            ? Objects.requireNonNull(tracer.currentSpan()).context().traceId()
            : null;

        String correlationId = exchange.getRequest().getHeaders().getFirst(Constants.CORRELATION_ID);

        MessageResponse response = new MessageResponse();
        response.setCode(ErrorCode.ERR_00503);
        response.setMessage(group.toUpperCase() + " service is temporarily unavailable.");
        response.setSuccess(false);
        response.setTimestamp(System.currentTimeMillis());
        response.setTraceId(traceId);
        response.setData(Collections.emptyMap());

        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(Constants.CORRELATION_ID, correlationId != null ? correlationId : "")
            .body(response));
    }
}
