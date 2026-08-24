package com.vendorpulse.platform.shift.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Input to {@link PricingEngineService#calculate}. */
public record ShiftPricingContext(
        UUID orgId,
        UUID shiftId,
        BigDecimal hourlyRate,
        BigDecimal regularHours,
        BigDecimal overtimeHours,
        BigDecimal holidayHours,
        BigDecimal markupPct
) {
}
