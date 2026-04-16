package com.epam.ratelimitergateway.gateway;

import com.epam.ratelimitergateway.config.GatewayRoutingProperties;
import com.epam.ratelimitergateway.security.InternalRoutingAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class GatewayProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final GatewayRoutingProperties routingProperties;
    private final RestTemplate restTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public GatewayProxyController(GatewayRoutingProperties routingProperties, RestTemplate restTemplate) {
        this.routingProperties = routingProperties;
        this.restTemplate = restTemplate;
    }

    @RequestMapping({"/{service}", "/{service}/**"})
    public ResponseEntity<?> proxy(
            @PathVariable String service,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        String baseUrl = routingProperties.getServices().get(service);
        if (!StringUtils.hasText(baseUrl)) {
            return ResponseEntity.notFound().build();
        }

        String subPath = extractSubPath(request);
        String targetUrl = buildTargetUrl(baseUrl, subPath, request.getQueryString());

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders outboundHeaders = copyHeaders(request);
        outboundHeaders.set(InternalRoutingAuthFilter.INTERNAL_ROUTING_HEADER, routingProperties.getInternalRoutingToken());
        HttpEntity<byte[]> outboundEntity = new HttpEntity<>(body, outboundHeaders);

        try {
            ResponseEntity<byte[]> upstream = restTemplate.exchange(targetUrl, method, outboundEntity, byte[].class);
            return ResponseEntity.status(upstream.getStatusCode())
                    .headers(filterHeaders(upstream.getHeaders()))
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(filterHeaders(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsByteArray());
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("error", "upstream_unavailable");
            payload.put("message", ex.getMessage());
            payload.put("target", targetUrl);
            payload.put("timestamp", Instant.now().toString());
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload);
        }
    }

    private String extractSubPath(HttpServletRequest request) {
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pathWithinMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (!StringUtils.hasText(bestMatchPattern) || !StringUtils.hasText(pathWithinMapping)) {
            return "";
        }
        return antPathMatcher.extractPathWithinPattern(bestMatchPattern, pathWithinMapping);
    }

    private String buildTargetUrl(String baseUrl, String subPath, String queryString) {
        StringBuilder builder = new StringBuilder(baseUrl);
        if (StringUtils.hasText(subPath)) {
            if (!baseUrl.endsWith("/")) {
                builder.append('/');
            }
            builder.append(subPath);
        }
        if (StringUtils.hasText(queryString)) {
            builder.append('?').append(queryString);
        }
        return builder.toString();
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            if (InternalRoutingAuthFilter.INTERNAL_ROUTING_HEADER.equalsIgnoreCase(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        return headers;
    }

    private HttpHeaders filterHeaders(HttpHeaders source) {
        HttpHeaders target = new HttpHeaders();
        if (source == null) {
            return target;
        }

        source.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                target.put(name, values);
            }
        });

        return target;
    }
}
