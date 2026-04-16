package com.epam.ratelimitergateway.security;

import com.epam.ratelimitergateway.config.GatewayRoutingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalRoutingAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_ROUTING_HEADER = "X-Internal-Routing-Token";

    private final GatewayRoutingProperties routingProperties;
    private final SecurityErrorResponseWriter errorWriter;

    public InternalRoutingAuthFilter(
            GatewayRoutingProperties routingProperties,
            SecurityErrorResponseWriter errorWriter
    ) {
        this.routingProperties = routingProperties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/backend/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String configuredToken = routingProperties.getInternalRoutingToken();
        if (!StringUtils.hasText(configuredToken)) {
            errorWriter.write(
                    response,
                    HttpStatus.FORBIDDEN.value(),
                    "backend_access_forbidden",
                    "Internal routing token is not configured"
            );
            return;
        }

        String receivedToken = request.getHeader(INTERNAL_ROUTING_HEADER);
        if (!configuredToken.equals(receivedToken)) {
            errorWriter.write(
                    response,
                    HttpStatus.FORBIDDEN.value(),
                    "backend_access_forbidden",
                    "Direct access to backend routes is not allowed"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
