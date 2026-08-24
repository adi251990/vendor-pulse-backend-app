package com.hireme.platform.attendance.dto;

public record ClockEventResponse(
        Long geoLogId,
        boolean withinGeofence,
        String bookingStatus,
        Double selfieVerificationScore,
        String syncStatus // ACCEPTED | PENDING_LATE_SYNC_REVIEW
) {
}
