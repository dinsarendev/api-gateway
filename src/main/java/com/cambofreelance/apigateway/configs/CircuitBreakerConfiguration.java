package com.cambofreelance.apigateway.configs;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.circuitbreaker.Customizer;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfiguration {

    /**
     * Default circuit breaker + time limiter settings applied to every
     * "{groupCode}-breaker" instance created by RouteLocatorDetail.
     *
     * Tuned for a gateway workload (fast failure detection, quick recovery probes):
     *
     *  CLOSED  → OPEN  : ≥50% failure rate over the last 10 calls (min 5 evaluated)
     *  OPEN    → HALF_OPEN : after 30s
     *  HALF_OPEN → CLOSED : 3 probe calls all succeed
     *  HALF_OPEN → OPEN   : any probe call fails
     *  Per-call timeout    : 10s (TimeLimiter)
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id ->
            new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowType(SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50.0f)
                    .slowCallRateThreshold(80.0f)
                    .slowCallDurationThreshold(Duration.ofSeconds(8))
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(10))
                    .build())
                .build());
    }
}