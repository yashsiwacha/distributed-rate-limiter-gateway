package com.epam.ratelimitergateway.security;

import com.epam.ratelimitergateway.config.RateLimitProperties;
import com.epam.ratelimitergateway.ratelimit.AlgorithmType;
import com.epam.ratelimitergateway.ratelimit.RateLimitDecision;
import com.epam.ratelimitergateway.ratelimit.RateLimitIdentity;
import com.epam.ratelimitergateway.ratelimit.RateLimitKeyResolver;
import com.epam.ratelimitergateway.ratelimit.RateLimitMetrics;
import com.epam.ratelimitergateway.ratelimit.RateLimitResult;
import com.epam.ratelimitergateway.ratelimit.RateLimitScope;
import com.epam.ratelimitergateway.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitMetrics metrics;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(
            RateLimiterService rateLimiterService,
            RateLimitKeyResolver keyResolver,
            RateLimitMetrics metrics,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper
    ) {
        this.rateLimiterService = rateLimiterService;
        this.keyResolver = keyResolver;
        this.metrics = metrics;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        RateLimitIdentity identity = keyResolver.resolve(request);
        RateLimitResult result = rateLimiterService.evaluate(request.getRequestURI(), identity);
        RateLimitDecision decision = result.decision();

        String algorithmTag = result.algorithm().name().toLowerCase();
        String scopeTag = result.scope().name().toLowerCase();
        long configuredLimit = resolveConfiguredLimit(result);

        response.setHeader("X-RateLimit-Algorithm", result.algorithm().name());
        response.setHeader("X-RateLimit-Limit", String.valueOf(configuredLimit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Scope", result.scope().name());
        if (decision.retryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        }

        if (decision.allowed()) {
            metrics.recordAllowed(algorithmTag, scopeTag);
            filterChain.doFilter(request, response);
            return;
        }

        metrics.recordRejected(algorithmTag, scopeTag);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", "rate_limit_exceeded");
        payload.put("algorithm", result.algorithm().name());
        payload.put("scope", result.scope().name());
        payload.put("retryAfterSeconds", decision.retryAfterSeconds());
        payload.put("remaining", decision.remaining());
        payload.put("fallbackMode", decision.fallbackUsed());

        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }

    private long resolveConfiguredLimit(RateLimitResult result) {
        RateLimitProperties.LimitProfile profile = result.scope() == RateLimitScope.USER
                ? rateLimitProperties.getUser()
                : rateLimitProperties.getIp();

        return result.algorithm() == AlgorithmType.TOKEN_BUCKET
                ? profile.getTokenBucket().getCapacity()
                : profile.getSlidingWindow().getMaxRequests();
    }
}
