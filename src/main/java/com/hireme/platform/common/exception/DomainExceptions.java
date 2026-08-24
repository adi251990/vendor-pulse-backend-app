package com.hireme.platform.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Concrete domain exceptions referenced directly by the API blueprint
 * (§3.C of the spec). Grouped in one file to keep the module tree flat;
 * split out per-module if the set grows much larger.
 */
public final class DomainExceptions {

    private DomainExceptions() {
    }

    public static class ShiftAlreadyClaimedException extends ApiException {
        public ShiftAlreadyClaimedException(java.util.UUID shiftId) {
            super(HttpStatus.CONFLICT, "SHIFT_ALREADY_CLAIMED",
                    "Shift " + shiftId + " has already been claimed");
        }
    }

    public static class ClaimFailedException extends ApiException {
        public ClaimFailedException(java.util.UUID shiftId, Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "CLAIM_LOCK_UNAVAILABLE",
                    "Could not acquire claim lock for shift " + shiftId);
            if (cause != null) {
                initCause(cause);
            }
        }
    }

    public static class ShiftNotClaimableException extends ApiException {
        public ShiftNotClaimableException(String reason) {
            super(HttpStatus.GONE, "SHIFT_NOT_CLAIMABLE", reason);
        }
    }

    public static class StaffNotEligibleException extends ApiException {
        public StaffNotEligibleException(String reason) {
            super(HttpStatus.FORBIDDEN, "STAFF_NOT_ELIGIBLE", reason);
        }
    }

    public static class OutsideGeofenceException extends ApiException {
        public OutsideGeofenceException(double distanceM) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "OUTSIDE_GEOFENCE",
                    "Clock event is " + distanceM + "m outside the permitted geofence");
        }

        public Map<String, Object> details(double distanceM) {
            return Map.of("error", "OUTSIDE_GEOFENCE", "distanceM", distanceM);
        }
    }

    public static class IdentityVerificationFailedException extends ApiException {
        public IdentityVerificationFailedException(double score) {
            super(HttpStatus.FORBIDDEN, "IDENTITY_VERIFICATION_FAILED",
                    "Selfie verification score " + score + " below required threshold");
        }
    }

    public static class AlreadyClockedInException extends ApiException {
        public AlreadyClockedInException() {
            super(HttpStatus.CONFLICT, "ALREADY_CLOCKED_IN", "Booking already has an open clock-in");
        }
    }

    public static class InvalidSignatureException extends ApiException {
        public InvalidSignatureException() {
            super(HttpStatus.FORBIDDEN, "INVALID_SIGNATURE", "Clock event signature failed verification");
        }
    }

    public static class NoPaymentMethodException extends ApiException {
        public NoPaymentMethodException() {
            super(HttpStatus.PAYMENT_REQUIRED, "NO_PAYMENT_METHOD",
                    "Vendor org has no valid payment method on file");
        }
    }

    public static class ResourceNotFoundException extends ApiException {
        public ResourceNotFoundException(String entity, Object id) {
            super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " " + id + " not found");
        }
    }

    public static class InvalidStateTransitionException extends ApiException {
        public InvalidStateTransitionException(String message) {
            super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", message);
        }
    }

    public static class AuthenticationFailedException extends ApiException {
        public AuthenticationFailedException(String message) {
            super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message);
        }
    }

    public static class DuplicateResourceException extends ApiException {
        public DuplicateResourceException(String message) {
            super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
        }
    }
}
