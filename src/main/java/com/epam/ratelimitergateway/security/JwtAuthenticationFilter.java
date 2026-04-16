package com.epam.ratelimitergateway.security;

import com.epam.ratelimitergateway.config.GatewaySecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REQUEST_USER_ID_ATTR = "REQUEST_USER_ID";

    private final JwtService jwtService;
    private final GatewaySecurityProperties securityProperties;
    private final SecurityErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            GatewaySecurityProperties securityProperties,
            SecurityErrorResponseWriter errorWriter
    ) {
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/auth/") || path.startsWith("/actuator/") || path.startsWith("/backend/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean apiPath = request.getRequestURI().startsWith("/api/");
        boolean jwtRequired = securityProperties.isRequireJwtForApi() && apiPath;

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            if (jwtRequired) {
                writeUnauthorized(response, "Missing bearer token");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        Optional<String> subject = jwtService.validateAndExtractSubject(token);

        if (subject.isEmpty()) {
            if (jwtRequired) {
                writeUnauthorized(response, "Invalid or expired JWT token");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String userId = subject.get();
        request.setAttribute(REQUEST_USER_ID_ATTR, userId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "unauthorized", message);
    }
}
