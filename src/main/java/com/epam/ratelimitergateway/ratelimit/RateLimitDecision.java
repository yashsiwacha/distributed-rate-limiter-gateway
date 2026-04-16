package com.epam.ratelimitergateway.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        boolean fallbackUsed
) {
}
