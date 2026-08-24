package com.hireme.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hireme.matching")
@Getter
@Setter
public class MatchingProperties {
    private Weights weights = new Weights();
    private double defaultRadiusKm = 25;

    @Getter
    @Setter
    public static class Weights {
        private double proximity = 0.35;
        private double rating = 0.25;
        private double skill = 0.25;
        private double reliability = 0.15;
    }
}
