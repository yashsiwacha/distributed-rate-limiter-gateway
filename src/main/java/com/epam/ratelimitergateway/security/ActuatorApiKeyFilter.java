package com.epam.ratelimitergateway.security;

import com.epam.ratelimitergateway.config.GatewaySecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActuatorApiKeyFilter extends OncePerRequestFilter {

    private static final String ACTUATOR_KEY_HEADER = "X-Actuator-Key";

    private final GatewaySecurityProperties securityProperties;
    private final SecurityErrorResponseWriter errorWriter;

    public ActuatorApiKeyFilter(
            GatewaySecurityProperties securityProperties,
            SecurityErrorResponseWriter errorWriter
    ) {
        this.securityProperties = securityProperties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/actuator/")) {
            return true;
        }
        return path.equals("/actuator/health") || path.equals("/actuator/info");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String configuredKey = securityProperties.getActuatorApiKey();
        if (!StringUtils.hasText(configuredKey)) {
            errorWriter.write(
                    response,
                    HttpStatus.FORBIDDEN.value(),
                    "actuator_access_forbidden",
                    "Actuator API key is not configured"
            );
            return;
        }

        String receivedKey = request.getHeader(ACTUATOR_KEY_HEADER);
        if (!configuredKey.equals(receivedKey)) {
            errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_actuator_key",
                    "Actuator API key is missing or invalid"
            );
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "actuator",
                null,
                AuthorityUtils.createAuthorityList("ROLE_ACTUATOR")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
