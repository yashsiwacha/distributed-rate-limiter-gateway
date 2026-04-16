package com.epam.ratelimitergateway.config;

import com.epam.ratelimitergateway.security.ActuatorApiKeyFilter;
import com.epam.ratelimitergateway.security.InternalRoutingAuthFilter;
import com.epam.ratelimitergateway.security.JwtAuthenticationFilter;
import com.epam.ratelimitergateway.security.RateLimitingFilter;
import com.epam.ratelimitergateway.security.SecurityErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ActuatorApiKeyFilter actuatorApiKeyFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalRoutingAuthFilter internalRoutingAuthFilter,
            RateLimitingFilter rateLimitingFilter,
            SecurityErrorResponseWriter errorWriter
    ) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; "
                                        + "img-src 'self' data:; font-src 'self' data:; object-src 'none'; "
                                        + "frame-ancestors 'none'; base-uri 'self'"
                        )))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "unauthorized", "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "forbidden", "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/showcase", "/showcase/", "/showcase/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/auth/token").permitAll()
                        .requestMatchers("/backend/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/**").hasRole("ACTUATOR")
                        .anyRequest().denyAll())
                .addFilterBefore(actuatorApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(internalRoutingAuthFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, InternalRoutingAuthFilter.class)
                .build();
    }
}
