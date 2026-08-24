package com.vendorpulse.platform.attendance.service;

import com.vendorpulse.platform.config.GeofenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceSignatureVerifierTest {

    private GeofenceProperties properties;
    private DeviceSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new GeofenceProperties();
        properties.setDeviceSharedSecret("test-secret-key-12345678901234567890");
        verifier = new DeviceSignatureVerifier(properties);
    }

    @Test
    void testCanonicalize() {
        String canonical = verifier.canonicalize("booking-1", "CLOCK_IN", "2026-09-01T08:00:00Z", 34.0522, -118.2437);
        assertEquals("booking-1|CLOCK_IN|2026-09-01T08:00:00Z|34.0522|-118.2437", canonical);
    }

    @Test
    void testValidSignature() throws Exception {
        String payload = "booking-1|CLOCK_IN|2026-09-01T08:00:00Z|34.0522|-118.2437";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getDeviceSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String validSig = Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        assertTrue(verifier.verify(payload, validSig));
    }

    @Test
    void testInvalidSignatureTamperedPayload() {
        String payload = "booking-1|CLOCK_IN|2026-09-01T08:00:00Z|34.0522|-118.2437";
        String tamperedPayload = "booking-1|CLOCK_IN|2026-09-01T08:00:00Z|35.0000|-118.2437";

        assertTrue(verifier.verify(payload, computeSig(payload)));
        assertFalse(verifier.verify(tamperedPayload, computeSig(payload)));
    }

    private String computeSig(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDeviceSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

