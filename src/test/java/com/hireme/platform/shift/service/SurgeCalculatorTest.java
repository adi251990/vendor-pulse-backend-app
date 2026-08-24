package com.hireme.platform.shift.service;

import com.hireme.platform.shift.entity.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurgeCalculatorTest {

    private SurgeCalculator surgeCalculator;

    @BeforeEach
    void setUp() {
        surgeCalculator = new SurgeCalculator();
    }

    @Test
    void testNoSurgeWhenFullyFilled() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .headcount(5)
                .filledCount(5)
                .startTime(now.plus(30, ChronoUnit.MINUTES))
                .build();

        BigDecimal multiplier = surgeCalculator.multiplierFor(shift, now);
        assertEquals(BigDecimal.ONE, multiplier);
    }

    @Test
    void testNoSurgeWhenStartTimeMoreThanTwoHoursAway() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .headcount(5)
                .filledCount(0)
                .startTime(now.plus(150, ChronoUnit.MINUTES)) // 2.5 hours away
                .build();

        BigDecimal multiplier = surgeCalculator.multiplierFor(shift, now);
        assertEquals(BigDecimal.ONE, multiplier);
    }

    @Test
    void testMaxSurgeWhenFullyUnfilledAndUnder30Minutes() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .headcount(4)
                .filledCount(0)
                .startTime(now.plus(20, ChronoUnit.MINUTES)) // 20 mins to start
                .build();

        BigDecimal multiplier = surgeCalculator.multiplierFor(shift, now);
        // Max surge is 1.5
        assertEquals(new BigDecimal("1.5000"), multiplier);
    }

    @Test
    void testPartialSurgeWhenPartiallyUnfilledAnd60MinutesOut() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .headcount(4)
                .filledCount(2) // 50% unfilled
                .startTime(now.plus(75, ChronoUnit.MINUTES)) // 75 mins to start
                .build();

        BigDecimal multiplier = surgeCalculator.multiplierFor(shift, now);
        assertTrue(multiplier.compareTo(BigDecimal.ONE) > 0);
        assertTrue(multiplier.compareTo(new BigDecimal("1.5")) < 0);
    }
}

