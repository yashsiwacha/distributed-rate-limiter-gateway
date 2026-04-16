package com.epam.ratelimitergateway.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RateLimitMetrics {

    private final MeterRegistry meterRegistry;

    public RateLimitMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAllowed(String algorithm, String scope) {
        Counter.builder("gateway_requests_allowed_total")
                .tag("algorithm", algorithm)
                .tag("scope", scope)
                .register(meterRegistry)
                .increment();
    }

    public void recordRejected(String algorithm, String scope) {
        Counter.builder("gateway_requests_rejected_total")
                .tag("algorithm", algorithm)
                .tag("scope", scope)
                .register(meterRegistry)
                .increment();
    }

    public void recordFallback(String algorithm, String scope) {
        Counter.builder("gateway_ratelimit_fallback_total")
                .tag("algorithm", algorithm)
                .tag("scope", scope)
                .register(meterRegistry)
                .increment();
    }

    public void recordRedisFailure(String algorithm) {
        Counter.builder("gateway_redis_failures_total")
                .tag("algorithm", algorithm)
                .register(meterRegistry)
                .increment();
    }

    public void recordCircuitOpen(String algorithm) {
        Counter.builder("gateway_redis_circuit_open_total")
                .tag("algorithm", algorithm)
                .register(meterRegistry)
                .increment();
    }

    public void recordRedisLatency(String algorithm, long nanos) {
        Timer.builder("gateway_redis_latency")
                .tag("algorithm", algorithm)
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }
}
