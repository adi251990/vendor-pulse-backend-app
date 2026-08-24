package com.vendorpulse.platform.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public record CreateShiftRequest(
        @NotBlank String title,
        List<String> requiredSkills,
        @NotNull @DecimalMin(value = "0.01") java.math.BigDecimal hourlyRate,
        @Min(1) int headcount,
        @NotNull @Valid GeoPoint siteCenter,
        @Min(10) int geofenceRadiusM,
        @NotNull @Future Instant startTime,
        @NotNull Instant endTime
) {
    public record GeoPoint(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lon
    ) {
    }
}
