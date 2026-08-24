package com.vendorpulse.platform.shift.service;

import com.vendorpulse.platform.config.PricingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingEngineServiceTest {

    private PricingProperties pricingProperties;
    private PricingEngineService pricingEngineService;

    @BeforeEach
    void setUp() {
        pricingProperties = new PricingProperties();
        pricingProperties.setOvertimeMultiplier(new BigDecimal("1.5"));
        pricingProperties.setHolidayMultiplier(new BigDecimal("2.0"));
        pricingEngineService = new PricingEngineService(pricingProperties);
    }

    @Test
    void testRegularHoursOnlyNoSurge() {
        ShiftPricingContext ctx = new ShiftPricingContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("20.00"), // hourly rate
                new BigDecimal("8.00"),  // regular hours
                BigDecimal.ZERO,         // overtime
                BigDecimal.ZERO,         // holiday
                new BigDecimal("0.2000") // 20% markup
        );

        PricingBreakdown result = pricingEngineService.estimate(ctx);

        // basePay = 8 * 20 = 160.00
        assertEquals(new BigDecimal("160.00"), result.basePay());
        // markup = 160 * 0.20 = 32.00
        assertEquals(new BigDecimal("32.00"), result.markupFee());
        // billRate = 160 + 32 = 192.00
        assertEquals(new BigDecimal("192.00"), result.billRate());
        assertEquals(BigDecimal.ONE, result.surgeMultiplier());
    }

    @Test
    void testOvertimeAndHolidayCalculations() {
        ShiftPricingContext ctx = new ShiftPricingContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("20.00"), // hourly rate
                new BigDecimal("8.00"),  // regular hours (8 * 20 = 160)
                new BigDecimal("2.00"),  // overtime hours (2 * 20 * 1.5 = 60)
                new BigDecimal("4.00"),  // holiday hours (4 * 20 * 2.0 = 160)
                new BigDecimal("0.2500") // 25% markup
        );

        // basePay = 160 + 60 + 160 = 380.00
        // markup = 380 * 0.25 = 95.00
        // billRate = 380 + 95 = 475.00
        PricingBreakdown result = pricingEngineService.estimate(ctx);

        assertEquals(new BigDecimal("380.00"), result.basePay());
        assertEquals(new BigDecimal("95.00"), result.markupFee());
        assertEquals(new BigDecimal("475.00"), result.billRate());
    }

    @Test
    void testSurgeMultiplierApplied() {
        ShiftPricingContext ctx = new ShiftPricingContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),  // regular pay = 100.00
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.2000") // markup rate = 20%
        );

        BigDecimal surgeMultiplier = new BigDecimal("1.2500");
        PricingBreakdown result = pricingEngineService.calculate(ctx, surgeMultiplier);

        // basePay = 100.00
        assertEquals(new BigDecimal("100.00"), result.basePay());
        // markupFee = 100 * 0.20 * 1.25 = 25.00
        assertEquals(new BigDecimal("25.00"), result.markupFee());
        // billRate = (100 + 25) * 1.25 = 156.25
        assertEquals(new BigDecimal("156.25"), result.billRate());
        assertEquals(surgeMultiplier, result.surgeMultiplier());
    }
}

