package com.hireme.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hireme.jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long accessTokenTtlMinutes = 15;
    private long refreshTokenTtlDays = 30;
}
