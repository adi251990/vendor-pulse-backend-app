package com.vendorpulse.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vendorpulse.noshow")
@Getter
@Setter
public class NoShowProperties {
    private int graceMinutes = 15;
    private String sweepCron = "0 * * * * *";
    private int suspendAfterCount90d = 3;
}
