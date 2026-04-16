package com.epam.ratelimitergateway;

import com.epam.ratelimitergateway.config.GatewayRoutingProperties;
import com.epam.ratelimitergateway.config.GatewaySecurityProperties;
import com.epam.ratelimitergateway.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableConfigurationProperties({
    RateLimitProperties.class,
    GatewayRoutingProperties.class,
    GatewaySecurityProperties.class
})
public class RateLimiterGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterGatewayApplication.class, args);
    }
}
