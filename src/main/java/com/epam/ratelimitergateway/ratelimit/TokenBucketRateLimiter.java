package com.epam.ratelimitergateway.ratelimit;

import com.epam.ratelimitergateway.config.RateLimitProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketRateLimiter {

    private static final String ALGORITHM = "token_bucket";

    private static final String TOKEN_BUCKET_LUA = """
            local tokenKey = KEYS[1]
            local tsKey = KEYS[2]
            local now = tonumber(ARGV[1])
            local refillTokens = tonumber(ARGV[2])
            local refillPeriodMs = tonumber(ARGV[3])
            local capacity = tonumber(ARGV[4])
            local requested = tonumber(ARGV[5])

            local tokens = tonumber(redis.call('GET', tokenKey))
            if tokens == nil then
                tokens = capacity
            end

            local lastRefill = tonumber(redis.call('GET', tsKey))
            if lastRefill == nil then
                lastRefill = now
            end

            if now > lastRefill then
                local elapsed = now - lastRefill
                local refill = math.floor((elapsed * refillTokens) / refillPeriodMs)
                if refill > 0 then
                    tokens = math.min(capacity, tokens + refill)
                    lastRefill = now
                end
            end

            local ttl = refillPeriodMs * 2
            local allowed = 0
            local retryAfter = 0

            if tokens >= requested then
                allowed = 1
                tokens = tokens - requested
            else
                local missing = requested - tokens
                retryAfter = math.ceil((missing * refillPeriodMs) / refillTokens / 1000)
            end

            redis.call('SET', tokenKey, tokens, 'PX', ttl)
            redis.call('SET', tsKey, lastRefill, 'PX', ttl)

            return {allowed, tokens, retryAfter}
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final InMemoryFallbackRateLimiter fallbackRateLimiter;
    private final RateLimitMetrics metrics;
    private final CircuitBreaker circuitBreaker;
    private final RedisScript<List> redisScript;

    public TokenBucketRateLimiter(
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
        script.setScriptText(TOKEN_BUCKET_LUA);
        script.setResultType(List.class);
        this.redisScript = script;
    }

    public RateLimitDecision allow(RateLimitIdentity identity) {
        RateLimitProperties.TokenBucket config = selectProfile(identity.scope()).getTokenBucket();

        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, () -> executeWithRedis(identity.key(), config)).get();
        } catch (CallNotPermittedException ex) {
            metrics.recordCircuitOpen(ALGORITHM);
            metrics.recordFallback(ALGORITHM, identity.scope().name().toLowerCase());
            return fallbackRateLimiter.allowTokenBucket(identity.key(), config);
        } catch (Exception ex) {
            metrics.recordRedisFailure(ALGORITHM);
            metrics.recordFallback(ALGORITHM, identity.scope().name().toLowerCase());
            return fallbackRateLimiter.allowTokenBucket(identity.key(), config);
        }
    }

    private RateLimitDecision executeWithRedis(String identityKey, RateLimitProperties.TokenBucket config) {
        long startedAt = System.nanoTime();
        long nowMillis = Instant.now().toEpochMilli();
        long refillPeriodMillis = Math.max(1L, config.getRefillPeriodSeconds() * 1000L);

        String keyBase = properties.getRedisPrefix() + ":tb:" + identityKey;
        List<String> keys = List.of(keyBase + ":tokens", keyBase + ":ts");

        List<?> raw = redisTemplate.execute(
                redisScript,
                keys,
                String.valueOf(nowMillis),
                String.valueOf(config.getRefillTokens()),
                String.valueOf(refillPeriodMillis),
                String.valueOf(config.getCapacity()),
                "1"
        );

        metrics.recordRedisLatency(ALGORITHM, System.nanoTime() - startedAt);

        if (raw == null || raw.size() < 2) {
            throw new IllegalStateException("Unexpected Redis script result for token bucket");
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
