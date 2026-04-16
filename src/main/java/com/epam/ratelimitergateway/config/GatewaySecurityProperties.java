package com.epam.ratelimitergateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private boolean requireJwtForApi = true;
    private boolean allowDemoTokenEndpoint = true;
    private String actuatorApiKey = "";

    public boolean isRequireJwtForApi() {
        return requireJwtForApi;
    }

    public void setRequireJwtForApi(boolean requireJwtForApi) {
        this.requireJwtForApi = requireJwtForApi;
    }

    public boolean isAllowDemoTokenEndpoint() {
        return allowDemoTokenEndpoint;
    }

    public void setAllowDemoTokenEndpoint(boolean allowDemoTokenEndpoint) {
        this.allowDemoTokenEndpoint = allowDemoTokenEndpoint;
    }

    public String getActuatorApiKey() {
        return actuatorApiKey;
    }

    public void setActuatorApiKey(String actuatorApiKey) {
        this.actuatorApiKey = actuatorApiKey;
    }
}
