package com.vendorpulse.platform.identity.entity;

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
@Table(name = "staff_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffProfile {

    /** Shares its PK with users.id (one-to-one profile extension). */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Column(name = "home_lat")
    private Double homeLat;

    @Column(name = "home_lon")
    private Double homeLon;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skill_tags", columnDefinition = "text[]")
    private List<String> skillTags;

    @Column(name = "avg_rating", nullable = false)
    private BigDecimal avgRating;

    @Column(name = "reliability_score", nullable = false)
    private BigDecimal reliabilityScore;

    @Column(name = "background_check_status", nullable = false)
    private String backgroundCheckStatus;

    @Column(name = "face_enrollment_ref")
    private String faceEnrollmentRef;

    @Column(name = "no_show_count_90d", nullable = false)
    private int noShowCount90d;

    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
