package com.epam.ratelimitergateway.ratelimit;

public record RateLimitResult(
        AlgorithmType algorithm,
        RateLimitScope scope,
        RateLimitDecision decision
) {
}
