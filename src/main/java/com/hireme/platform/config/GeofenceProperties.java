package com.hireme.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hireme.geofence")
@Getter
@Setter
public class GeofenceProperties {
    private double accuracyBufferM = 50;
    private long offlineSyncGraceHours = 4;
    private double selfieVerificationThreshold = 0.80;
    /**
     * Dev-only shared secret used to verify the HMAC signature on clock
     * events. Production should replace this with a per-device key issued
     * at enrollment (see DeviceSignatureVerifier javadoc).
     */
    private String deviceSharedSecret = "dev-only-shared-device-secret-change-me";
}
