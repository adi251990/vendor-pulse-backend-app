package com.vendorpulse.platform.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public record ClockEventRequest(
        @NotNull UUID bookingId,
        @NotNull Instant recordedAt,
        @NotNull @Valid Location location,
        @NotBlank String deviceAttestationToken,
        String selfieImageRef,
        /** Optional: pre-computed by an on-device or upstream face-match step; null skips the identity check. */
        @DecimalMin("0.0") @DecimalMax("1.0") Double selfieVerificationScore,
        @NotBlank String signature
) {
    public record Location(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lon,
            @DecimalMin("0.0") double accuracyM
    ) {
    }
}
