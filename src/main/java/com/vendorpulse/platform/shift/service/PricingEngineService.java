package com.vendorpulse.platform.shift.service;

import com.vendorpulse.platform.config.PricingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dynamic Pricing Engine — spec §2.A.
 *
 * <pre>
 * base_worker_pay      = regular_hours * hourly_rate
 *                      + overtime_hours * hourly_rate * OT_MULTIPLIER
 *                      + holiday_hours  * hourly_rate * HOLIDAY_MULTIPLIER
 * platform_markup_fee  = base_worker_pay * markup_pct * surge_multiplier
 * vendor_bill_rate     = (base_worker_pay + platform_markup_fee) * surge_multiplier
 * </pre>
 *
 * All monetary math is done in BigDecimal with 4 decimal places of
 * intermediate precision and only rounded to 2 decimals (HALF_EVEN,
 * "banker's rounding") at the very end, to avoid compounding rounding error
 * across the three components before the final invoice figure is struck.
 */
@Service
public class PricingEngineService {

    private static final int INTERMEDIATE_SCALE = 4;
    private static final int FINAL_SCALE = 2;

    private final PricingProperties pricingProperties;

    public PricingEngineService(PricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    public PricingBreakdown calculate(ShiftPricingContext ctx, BigDecimal surgeMultiplier) {
        BigDecimal regularPay = ctx.regularHours().multiply(ctx.hourlyRate());

        BigDecimal otPay = ctx.overtimeHours()
                .multiply(ctx.hourlyRate())
                .multiply(pricingProperties.getOvertimeMultiplier());

        BigDecimal holidayPay = ctx.holidayHours()
                .multiply(ctx.hourlyRate())
                .multiply(pricingProperties.getHolidayMultiplier());

        BigDecimal basePayRaw = regularPay.add(otPay).add(holidayPay)
                .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN);

        BigDecimal markupFeeRaw = basePayRaw
                .multiply(ctx.markupPct())
                .multiply(surgeMultiplier)
                .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN);

        BigDecimal billRateRaw = basePayRaw.add(markupFeeRaw)
                .multiply(surgeMultiplier)
                .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN);

        return new PricingBreakdown(
                basePayRaw.setScale(FINAL_SCALE, RoundingMode.HALF_EVEN),
                markupFeeRaw.setScale(FINAL_SCALE, RoundingMode.HALF_EVEN),
                billRateRaw.setScale(FINAL_SCALE, RoundingMode.HALF_EVEN),
                surgeMultiplier
        );
    }

    /** Convenience overload for the common "no surge yet" estimate shown at shift-creation time. */
    public PricingBreakdown estimate(ShiftPricingContext ctx) {
        return calculate(ctx, BigDecimal.ONE);
    }
}
