package com.hireme.platform.shift.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_skills", columnDefinition = "text[]")
    private List<String> requiredSkills;

    @Column(name = "hourly_rate", nullable = false)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private int headcount;

    @Column(name = "filled_count", nullable = false)
    private int filledCount;

    @Column(name = "site_lat", nullable = false)
    private double siteLat;

    @Column(name = "site_lon", nullable = false)
    private double siteLon;

    @Column(name = "geofence_radius_m", nullable = false)
    private int geofenceRadiusM;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    /** Optimistic-lock guard — see ClaimService for how this backstops the Redisson lock. */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isClaimable() {
        return (status == ShiftStatus.PUBLISHED || status == ShiftStatus.MATCHING)
                && filledCount < headcount;
    }
}
