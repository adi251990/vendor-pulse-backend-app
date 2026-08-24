package com.hireme.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "hireme.pricing")
@Getter
@Setter
public class PricingProperties {
    private BigDecimal overtimeMultiplier = new BigDecimal("1.5");
    private BigDecimal holidayMultiplier = new BigDecimal("2.0");
    private BigDecimal dailyOvertimeThresholdHours = new BigDecimal("8");
    private BigDecimal weeklyOvertimeThresholdHours = new BigDecimal("40");
}
