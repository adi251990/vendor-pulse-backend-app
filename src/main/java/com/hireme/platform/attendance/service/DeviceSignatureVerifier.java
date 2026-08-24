package com.hireme.platform.attendance.service;

import com.hireme.platform.config.GeofenceProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Verifies the HMAC signature the Android app attaches to every clock event
 * (spec §2.C tamper-proofing). This scaffold signs with a single shared
 * platform secret; production should switch to a per-device key issued at
 * enrollment (stored in a DeviceEnrollment table, rotated on re-install) so
 * a leaked secret only compromises one device rather than the whole fleet.
 */
@Component
public class DeviceSignatureVerifier {

    private final GeofenceProperties properties;

    public DeviceSignatureVerifier(GeofenceProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String canonicalPayload, String providedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDeviceSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(computed);
            return expected.equals(providedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    public String canonicalize(String bookingId, String eventType, String recordedAtIso, double lat, double lon) {
        return bookingId + "|" + eventType + "|" + recordedAtIso + "|" + lat + "|" + lon;
    }
}
