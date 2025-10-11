package com.cambofreelance.apigateway.controllers;

import io.micrometer.tracing.Tracer;
import java.util.Collections;
import java.util.Objects;
import com.cambofreelance.apigateway.exception.MessageResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    private final Tracer tracer;

    public GatewayFallbackController(Tracer tracer) {
        this.tracer = tracer;
    }

    @PostMapping("/{group}")
    public Mono<MessageResponse> fallback(@PathVariable String group) {
        String traceId = tracer.currentSpan() != null
                ? Objects.requireNonNull(tracer.currentSpan()).context().traceId()
                : null;

        MessageResponse response = new MessageResponse();
        response.setCode("ERR_FALLBACK");
        response.setMessage(group.toUpperCase() + " service is temporarily unavailable.");
        response.setSuccess(false);
        response.setTimestamp(System.currentTimeMillis());
        response.setTraceId(traceId);
        response.setData(Collections.emptyMap());

        return Mono.just(response);
    }
}
