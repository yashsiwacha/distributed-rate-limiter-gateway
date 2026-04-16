package com.epam.ratelimitergateway.ratelimit;

import com.epam.ratelimitergateway.config.RateLimitProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowLogRateLimiter {

    private static final String ALGORITHM = "sliding_window_log";

    private static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local requestId = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)
            local count = redis.call('ZCARD', key)

            if count >= maxRequests then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retryAfter = 1
                if oldest[2] ~= nil then
                    retryAfter = math.ceil((windowMs - (now - tonumber(oldest[2]))) / 1000)
                    if retryAfter < 1 then
                        retryAfter = 1
                    end
                end
                return {0, 0, retryAfter}
            end

            redis.call('ZADD', key, now, requestId)
            redis.call('PEXPIRE', key, windowMs)
            count = count + 1
            return {1, maxRequests - count, 0}
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final InMemoryFallbackRateLimiter fallbackRateLimiter;
    private final RateLimitMetrics metrics;
    private final CircuitBreaker circuitBreaker;
    private final RedisScript<List> redisScript;

    public SlidingWindowLogRateLimiter(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties,
            InMemoryFallbackRateLimiter fallbackRateLimiter,
            RateLimitMetrics metrics,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.fallbackRateLimiter = fallbackRateLimiter;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimiter");

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(SLIDING_WINDOW_LUA);
        script.setResultType(List.class);
        this.redisScript = script;
    }

    public RateLimitDecision allow(RateLimitIdentity identity) {
        RateLimitProperties.SlidingWindow config = selectProfile(identity.scope()).getSlidingWindow();

        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, () -> executeWithRedis(identity.key(), config)).get();
        } catch (CallNotPermittedException ex) {
            metrics.recordCircuitOpen(ALGORITHM);
            metrics.recordFallback(ALGORITHM, identity.scope().name().toLowerCase());
            return fallbackRateLimiter.allowSlidingWindow(identity.key(), config);
        } catch (Exception ex) {
            metrics.recordRedisFailure(ALGORITHM);
            metrics.recordFallback(ALGORITHM, identity.scope().name().toLowerCase());
            return fallbackRateLimiter.allowSlidingWindow(identity.key(), config);
        }
    }

    private RateLimitDecision executeWithRedis(String identityKey, RateLimitProperties.SlidingWindow config) {
        long startedAt = System.nanoTime();
        long nowMillis = Instant.now().toEpochMilli();
        long windowMillis = Math.max(1L, config.getWindowSeconds() * 1000L);

        String redisKey = properties.getRedisPrefix() + ":sw:" + identityKey;
        List<String> keys = List.of(redisKey);

        List<?> raw = redisTemplate.execute(
                redisScript,
                keys,
                String.valueOf(nowMillis),
                String.valueOf(windowMillis),
                String.valueOf(config.getMaxRequests()),
                UUID.randomUUID().toString()
        );

        metrics.recordRedisLatency(ALGORITHM, System.nanoTime() - startedAt);

        if (raw == null || raw.size() < 2) {
            throw new IllegalStateException("Unexpected Redis script result for sliding window");
        }

        boolean allowed = toLong(raw.get(0)) == 1L;
        long remaining = Math.max(0L, toLong(raw.get(1)));
        long retryAfter = raw.size() > 2 ? Math.max(0L, toLong(raw.get(2))) : 0L;

        return new RateLimitDecision(allowed, remaining, retryAfter, false);
    }

    private RateLimitProperties.LimitProfile selectProfile(RateLimitScope scope) {
        return scope == RateLimitScope.USER ? properties.getUser() : properties.getIp();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
