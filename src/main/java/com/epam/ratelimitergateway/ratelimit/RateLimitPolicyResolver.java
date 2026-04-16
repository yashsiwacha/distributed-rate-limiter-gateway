package com.epam.ratelimitergateway.ratelimit;

import com.epam.ratelimitergateway.config.RateLimitProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class RateLimitPolicyResolver {

    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitPolicyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public AlgorithmType resolveAlgorithm(String requestPath) {
        for (Map.Entry<String, String> rule : properties.getAlgorithmByRoute().entrySet()) {
            String routeKey = rule.getKey();
            if (matchesServiceKeyRoute(routeKey, requestPath) || pathMatcher.match(routeKey, requestPath)) {
                return AlgorithmType.fromValue(rule.getValue()).orElse(AlgorithmType.TOKEN_BUCKET);
            }
        }
        return AlgorithmType.TOKEN_BUCKET;
    }

    private boolean matchesServiceKeyRoute(String routeKey, String requestPath) {
        String normalized = routeKey == null ? "" : routeKey.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return requestPath.equals("/api/" + normalized)
                || requestPath.startsWith("/api/" + normalized + "/");
    }
}
