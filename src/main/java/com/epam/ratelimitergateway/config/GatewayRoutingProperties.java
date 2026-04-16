package com.epam.ratelimitergateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.routing")
public class GatewayRoutingProperties {

    private Map<String, String> services = new LinkedHashMap<>();
    private String internalRoutingToken = "local-dev-internal-token-change-me";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;

    public Map<String, String> getServices() {
        return services;
    }

    public void setServices(Map<String, String> services) {
        this.services = services;
    }

    public String getInternalRoutingToken() {
        return internalRoutingToken;
    }

    public void setInternalRoutingToken(String internalRoutingToken) {
        this.internalRoutingToken = internalRoutingToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
