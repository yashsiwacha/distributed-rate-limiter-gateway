package com.epam.ratelimitergateway.backend;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backend/service-b")
public class ServiceBController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(
            @RequestParam(defaultValue = "10") long delayMs
    ) throws InterruptedException {
        Thread.sleep(Math.max(0L, delayMs));
        return ResponseEntity.ok(Map.of(
                "service", "service-b",
                "status", "ok",
                "timestamp", Instant.now().toString()
        ));
    }
}
