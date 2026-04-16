package com.epam.ratelimitergateway.ratelimit;

import java.util.Arrays;
import java.util.Optional;

public enum AlgorithmType {
    TOKEN_BUCKET,
    SLIDING_WINDOW_LOG;

    public static Optional<AlgorithmType> fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
