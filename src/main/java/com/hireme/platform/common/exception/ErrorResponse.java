package com.hireme.platform.common.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String error,
        String message,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, Instant.now(), Map.of());
    }

    public static ErrorResponse of(String error, String message, Map<String, Object> details) {
        return new ErrorResponse(error, message, Instant.now(), details);
    }
}
