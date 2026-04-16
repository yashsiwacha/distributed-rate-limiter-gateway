package com.epam.ratelimitergateway.ratelimit;

import com.epam.ratelimitergateway.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RateLimitKeyResolver {

    public RateLimitIdentity resolve(HttpServletRequest request) {
        Object subject = request.getAttribute(JwtAuthenticationFilter.REQUEST_USER_ID_ATTR);
        if (subject instanceof String userId && StringUtils.hasText(userId)) {
            return new RateLimitIdentity("user:" + userId, RateLimitScope.USER);
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwardedFor)
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();

        return new RateLimitIdentity("ip:" + ip, RateLimitScope.IP);
    }
}
