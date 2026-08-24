package com.hireme.platform.attendance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "geo_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private GeoEventType eventType;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "accuracy_m")
    private BigDecimal accuracyM;

    @Column(name = "within_geofence", nullable = false)
    private boolean withinGeofence;

    @Column(name = "device_attestation_ok", nullable = false)
    private boolean deviceAttestationOk;

    @Column(name = "mock_location_suspected", nullable = false)
    private boolean mockLocationSuspected;

    @Column(name = "selfie_verification_score")
    private BigDecimal selfieVerificationScore;

    @Column(nullable = false)
    private String signature;
}
