package com.epam.ratelimitergateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    private String redisPrefix = "rl";
    private Map<String, String> algorithmByRoute = new LinkedHashMap<>();
    private LimitProfile user = new LimitProfile();
    private LimitProfile ip = new LimitProfile();

    public String getRedisPrefix() {
        return redisPrefix;
    }

    public void setRedisPrefix(String redisPrefix) {
        this.redisPrefix = redisPrefix;
    }

    public Map<String, String> getAlgorithmByRoute() {
        return algorithmByRoute;
    }

    public void setAlgorithmByRoute(Map<String, String> algorithmByRoute) {
        this.algorithmByRoute = algorithmByRoute;
    }

    public LimitProfile getUser() {
        return user;
    }

    public void setUser(LimitProfile user) {
        this.user = user;
    }

    public LimitProfile getIp() {
        return ip;
    }

    public void setIp(LimitProfile ip) {
        this.ip = ip;
    }

    public static class LimitProfile {
        private TokenBucket tokenBucket = new TokenBucket();
        private SlidingWindow slidingWindow = new SlidingWindow();

        public TokenBucket getTokenBucket() {
            return tokenBucket;
        }

        public void setTokenBucket(TokenBucket tokenBucket) {
            this.tokenBucket = tokenBucket;
        }

        public SlidingWindow getSlidingWindow() {
            return slidingWindow;
        }

        public void setSlidingWindow(SlidingWindow slidingWindow) {
            this.slidingWindow = slidingWindow;
        }
    }

    public static class TokenBucket {
        private int capacity = 60;
        private int refillTokens = 60;
        private int refillPeriodSeconds = 60;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public int getRefillPeriodSeconds() {
            return refillPeriodSeconds;
        }

        public void setRefillPeriodSeconds(int refillPeriodSeconds) {
            this.refillPeriodSeconds = refillPeriodSeconds;
        }
    }

    public static class SlidingWindow {
        private int maxRequests = 100;
        private int windowSeconds = 60;

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
