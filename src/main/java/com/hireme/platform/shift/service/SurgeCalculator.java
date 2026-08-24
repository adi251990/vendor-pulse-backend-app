package com.hireme.platform.shift.service;

import com.hireme.platform.shift.entity.Shift;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Applies the surge multiplier referenced in spec §2.A / the notification
 * cascade in §2.B: shifts that are both close to start time and badly
 * understaffed get a pay (and bill-rate) bump to attract last-minute claims.
 */
@Component
public class SurgeCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal MAX_SURGE = new BigDecimal("1.5");

    public BigDecimal multiplierFor(Shift shift) {
        return multiplierFor(shift, Instant.now());
    }

    public BigDecimal multiplierFor(Shift shift, Instant now) {
        if (shift.getHeadcount() <= 0) {
            return ONE;
        }

        double unfilledRatio = 1.0 - ((double) shift.getFilledCount() / shift.getHeadcount());
        long minutesToStart = Duration.between(now, shift.getStartTime()).toMinutes();

        if (unfilledRatio <= 0 || minutesToStart > 120) {
            return ONE;
        }

        // Linear ramp: fully unfilled + <=30min to start => max surge.
        // Fully unfilled + ~120min to start => modest surge.
        double urgencyFactor = Math.max(0.0, Math.min(1.0, (120.0 - minutesToStart) / 90.0));
        double surge = 1.0 + (MAX_SURGE.doubleValue() - 1.0) * unfilledRatio * urgencyFactor;

        return BigDecimal.valueOf(surge).setScale(4, RoundingMode.HALF_EVEN);
    }
}
