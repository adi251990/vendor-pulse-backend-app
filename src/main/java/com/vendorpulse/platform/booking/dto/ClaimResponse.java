package com.vendorpulse.platform.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ClaimResponse(UUID bookingId, UUID shiftId, String status, BigDecimal matchScore) {
}
