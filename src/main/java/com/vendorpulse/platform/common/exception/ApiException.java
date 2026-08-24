package com.vendorpulse.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all domain exceptions that carry an HTTP status and a
 * machine-readable error code (matches the "{ error: CODE }" shape used
 * throughout the API blueprint in the system spec).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
