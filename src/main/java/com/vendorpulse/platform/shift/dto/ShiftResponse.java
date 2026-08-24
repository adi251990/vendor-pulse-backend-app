package com.vendorpulse.platform.shift.dto;

import com.vendorpulse.platform.shift.entity.Shift;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        String title,
        String status,
        BigDecimal hourlyRate,
        BigDecimal estimatedBillRate,
        int headcount,
        int filledCount,
        Instant startTime,
        Instant endTime,
        int version
) {
    public static ShiftResponse from(Shift shift, BigDecimal estimatedBillRate) {
        return new ShiftResponse(
                shift.getId(),
                shift.getTitle(),
                shift.getStatus().name(),
                shift.getHourlyRate(),
                estimatedBillRate,
                shift.getHeadcount(),
                shift.getFilledCount(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getVersion());
    }
}
