package com.vendorpulse.platform.shift.service;

import java.math.BigDecimal;

/**
 * Result of the dynamic pricing engine (spec §2.A). {@code basePay} is what
 * the worker earns; {@code billRate} is what the vendor is charged;
 * {@code markupFee} is the platform's take. All three already include the
 * surge multiplier that was in effect at calculation time.
 */
public record PricingBreakdown(
        BigDecimal basePay,
        BigDecimal markupFee,
        BigDecimal billRate,
        BigDecimal surgeMultiplier
) {
}
