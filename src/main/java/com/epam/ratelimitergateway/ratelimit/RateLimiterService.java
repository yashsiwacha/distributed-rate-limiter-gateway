package com.epam.ratelimitergateway.ratelimit;

import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private final RateLimitPolicyResolver policyResolver;
    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final SlidingWindowLogRateLimiter slidingWindowLogRateLimiter;

    public RateLimiterService(
            RateLimitPolicyResolver policyResolver,
            TokenBucketRateLimiter tokenBucketRateLimiter,
            SlidingWindowLogRateLimiter slidingWindowLogRateLimiter
    ) {
        this.policyResolver = policyResolver;
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
        this.slidingWindowLogRateLimiter = slidingWindowLogRateLimiter;
    }

    public RateLimitResult evaluate(String requestPath, RateLimitIdentity identity) {
        AlgorithmType algorithm = policyResolver.resolveAlgorithm(requestPath);
        RateLimitDecision decision = switch (algorithm) {
            case TOKEN_BUCKET -> tokenBucketRateLimiter.allow(identity);
            case SLIDING_WINDOW_LOG -> slidingWindowLogRateLimiter.allow(identity);
        };

        return new RateLimitResult(algorithm, identity.scope(), decision);
    }
}
