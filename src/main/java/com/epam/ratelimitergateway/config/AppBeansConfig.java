package com.epam.ratelimitergateway.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppBeansConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, GatewayRoutingProperties routingProperties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(Math.max(1, routingProperties.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(1, routingProperties.getReadTimeoutMs())))
                .build();
    }
}
