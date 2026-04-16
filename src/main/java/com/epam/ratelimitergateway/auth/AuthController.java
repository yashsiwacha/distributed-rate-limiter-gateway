package com.epam.ratelimitergateway.auth;

import com.epam.ratelimitergateway.config.GatewaySecurityProperties;
import com.epam.ratelimitergateway.security.JwtService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final GatewaySecurityProperties securityProperties;

    public AuthController(JwtService jwtService, GatewaySecurityProperties securityProperties) {
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> generateToken(
            @RequestParam(defaultValue = "demo-user") String userId
    ) {
        if (!securityProperties.isAllowDemoTokenEndpoint()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "not_found");
            payload.put("message", "Token issuance endpoint is disabled in this environment");
            payload.put("timestamp", Instant.now().toString());
            return ResponseEntity.status(404).body(payload);
        }

        if (!StringUtils.hasText(userId)) {
            userId = "demo-user";
        }

        String token = jwtService.generateToken(userId);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", userId,
                "issuedAt", Instant.now().toString()
        ));
    }
}
