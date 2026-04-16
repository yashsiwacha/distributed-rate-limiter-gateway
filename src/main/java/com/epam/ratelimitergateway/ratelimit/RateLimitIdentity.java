package com.epam.ratelimitergateway.ratelimit;

public record RateLimitIdentity(String key, RateLimitScope scope) {
}
