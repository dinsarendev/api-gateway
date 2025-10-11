package com.cambofreelance.apigateway.exception;

import io.micrometer.tracing.Tracer;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Tracer tracer;

    @ExceptionHandler(PathNotFoundException.class)
    public Mono<MessageResponse> handlePathNotFound(PathNotFoundException ex, ServerWebExchange exchange) {
        return buildMessageResponse("404", "Path not found: " + ex.getMessage(), HttpStatus.NOT_FOUND, exchange);
    }

    @ExceptionHandler(UriNotFoundException.class)
    public Mono<MessageResponse> handleUriNotFound(UriNotFoundException ex, ServerWebExchange exchange) {
        return buildMessageResponse("404", "URI not found: " + ex.getMessage(), HttpStatus.NOT_FOUND, exchange);
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public Mono<MessageResponse> handleRouteNotFoundException(RouteNotFoundException ex, ServerWebExchange exchange) {
        return buildMessageResponse("404", "Route not found: " + ex.getMessage(), HttpStatus.NOT_FOUND, exchange);
    }

    @ExceptionHandler(UnknownHostException.class)
    public Mono<MessageResponse> handleUnknownHost(UnknownHostException ex, ServerWebExchange exchange) {
        return buildMessageResponse("503", "Our service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE, exchange);
    }

    @ExceptionHandler(TimeoutException.class)
    public Mono<MessageResponse> handleTimeout(TimeoutException ex, ServerWebExchange exchange) {
        return buildMessageResponse("504", "Request timed out", HttpStatus.GATEWAY_TIMEOUT, exchange);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<MessageResponse> handleWebClientResponse(WebClientResponseException ex, ServerWebExchange exchange) {
        return buildMessageResponse(
                String.valueOf(ex.getRawStatusCode()),
                "Downstream service error: " + ex.getMessage(),
                (HttpStatus) ex.getStatusCode(),
                exchange
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<MessageResponse> handleRuntimeException(RuntimeException ex, ServerWebExchange exchange) {
        Throwable rootCause = getRootCause(ex);
        if (rootCause instanceof UnknownHostException || ex.getMessage().contains("NXDOMAIN")) {
            return buildMessageResponse("503", "Our service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE, exchange);
        }

        return buildMessageResponse("500", "Unexpected error occurred: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, exchange);
    }

    private Mono<MessageResponse> buildMessageResponse(String code, String message, HttpStatus status, ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(status);

        MessageResponse response = new MessageResponse();
        response.setCode(code);
        response.setMessage(message);
        response.setSuccess(false);
        response.setTimestamp(System.currentTimeMillis());
        response.setData(Collections.emptyMap());

        if (tracer.currentSpan() != null) {
            response.setTraceId(Objects.requireNonNull(tracer.currentSpan()).context().traceId());
        }

        return Mono.just(response);
    }

    private Throwable getRootCause(@NonNull Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
