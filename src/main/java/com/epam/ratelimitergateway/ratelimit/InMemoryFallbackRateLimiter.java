package com.epam.ratelimitergateway.ratelimit;

import com.epam.ratelimitergateway.config.RateLimitProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryFallbackRateLimiter {

    private final Map<String, LocalTokenBucket> tokenBuckets = new ConcurrentHashMap<>();
    private final Map<String, SlidingLogWindow> slidingWindows = new ConcurrentHashMap<>();

    public RateLimitDecision allowTokenBucket(String key, RateLimitProperties.TokenBucket config) {
        long nowMillis = Instant.now().toEpochMilli();
        LocalTokenBucket bucket = tokenBuckets.computeIfAbsent(key, ignored ->
                new LocalTokenBucket(config.getCapacity(), nowMillis));

        synchronized (bucket) {
            long periodMs = Math.max(1L, config.getRefillPeriodSeconds() * 1000L);
            double refillRatePerMs = (double) config.getRefillTokens() / periodMs;
            long elapsed = Math.max(0L, nowMillis - bucket.lastRefillMillis);
            bucket.tokens = Math.min(config.getCapacity(), bucket.tokens + (elapsed * refillRatePerMs));
            bucket.lastRefillMillis = nowMillis;

            if (bucket.tokens >= 1.0d) {
                bucket.tokens -= 1.0d;
                long remaining = (long) Math.floor(bucket.tokens);
                return new RateLimitDecision(true, remaining, 0L, true);
            }

            long retryAfterSeconds;
            if (refillRatePerMs <= 0.0d) {
                retryAfterSeconds = config.getRefillPeriodSeconds();
            } else {
                double missing = 1.0d - bucket.tokens;
                retryAfterSeconds = (long) Math.ceil((missing / refillRatePerMs) / 1000.0d);
                retryAfterSeconds = Math.max(1L, retryAfterSeconds);
            }
            return new RateLimitDecision(false, 0L, retryAfterSeconds, true);
        }
    }

    public RateLimitDecision allowSlidingWindow(String key, RateLimitProperties.SlidingWindow config) {
        long nowMillis = Instant.now().toEpochMilli();
        long windowMs = Math.max(1L, config.getWindowSeconds() * 1000L);
        SlidingLogWindow window = slidingWindows.computeIfAbsent(key, ignored -> new SlidingLogWindow());

        synchronized (window) {
            while (!window.timestamps.isEmpty() && window.timestamps.peekFirst() <= nowMillis - windowMs) {
                window.timestamps.removeFirst();
            }

            if (window.timestamps.size() >= config.getMaxRequests()) {
                long oldest = window.timestamps.peekFirst();
                long retryAfterMs = Math.max(1L, windowMs - (nowMillis - oldest));
                long retryAfterSeconds = (long) Math.ceil(retryAfterMs / 1000.0d);
                return new RateLimitDecision(false, 0L, retryAfterSeconds, true);
            }

            window.timestamps.addLast(nowMillis);
            long remaining = Math.max(0, config.getMaxRequests() - window.timestamps.size());
            return new RateLimitDecision(true, remaining, 0L, true);
        }
    }

    private static final class LocalTokenBucket {
        private double tokens;
        private long lastRefillMillis;

        private LocalTokenBucket(double tokens, long lastRefillMillis) {
            this.tokens = tokens;
            this.lastRefillMillis = lastRefillMillis;
        }
    }

    private static final class SlidingLogWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();
    }
}
