package com.vendorpulse.platform.booking.service;

import com.vendorpulse.platform.config.MatchingProperties;
import com.vendorpulse.platform.identity.entity.StaffProfile;
import com.vendorpulse.platform.identity.repository.StaffProfileRepository;
import com.vendorpulse.platform.shift.entity.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private StaffProfileRepository staffProfileRepository;

    private MatchingProperties matchingProperties;
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingProperties = new MatchingProperties();
        matchingProperties.setDefaultRadiusKm(25.0);
        MatchingProperties.Weights weights = new MatchingProperties.Weights();
        weights.setProximity(0.35);
        weights.setRating(0.25);
        weights.setSkill(0.25);
        weights.setReliability(0.15);
        matchingProperties.setWeights(weights);

        matchingService = new MatchingService(staffProfileRepository, matchingProperties);
    }

    @Test
    void testScorePerfectMatch() {
        UUID staffId = UUID.randomUUID();
        // Exact same location (proximity = 1.0), 5-star rating (1.0), exact matching skills (1.0), 100% reliability (1.0)
        StaffProfile profile = StaffProfile.builder()
                .userId(staffId)
                .homeLat(34.0522)
                .homeLon(-118.2437)
                .avgRating(new BigDecimal("5.00"))
                .skillTags(List.of("FORKLIFT", "HEAVY_LIFTING"))
                .reliabilityScore(BigDecimal.ONE)
                .build();

        Shift shift = Shift.builder()
                .siteLat(34.0522)
                .siteLon(-118.2437)
                .requiredSkills(List.of("FORKLIFT", "HEAVY_LIFTING"))
                .build();

        MatchingService.ScoredCandidate candidate = matchingService.score(profile, shift);

        assertEquals(staffId, candidate.staffId());
        assertEquals(new BigDecimal("1.0000"), candidate.score());
        assertEquals(0.0, candidate.distanceMeters(), 1.0);
    }

    @Test
    void testPartialSkillMatchJaccard() {
        UUID staffId = UUID.randomUUID();
        StaffProfile profile = StaffProfile.builder()
                .userId(staffId)
                .homeLat(34.0522)
                .homeLon(-118.2437)
                .avgRating(new BigDecimal("5.00")) // 1.0 * 0.25 = 0.25
                .skillTags(List.of("FORKLIFT"))     // 1 shared out of 2 total => 0.5 * 0.25 = 0.125
                .reliabilityScore(BigDecimal.ONE)   // 1.0 * 0.15 = 0.15
                .build();

        Shift shift = Shift.builder()
                .siteLat(34.0522)
                .siteLon(-118.2437)                 // proximity = 1.0 * 0.35 = 0.35
                .requiredSkills(List.of("FORKLIFT", "HEAVY_LIFTING"))
                .build();

        MatchingService.ScoredCandidate candidate = matchingService.score(profile, shift);

        // Expected: 0.35 + 0.25 + 0.125 + 0.15 = 0.8750
        assertEquals(new BigDecimal("0.8750"), candidate.score());
    }

    @Test
    void testCandidateOutsideMaxRadiusGetsZeroProximity() {
        UUID staffId = UUID.randomUUID();
        // Location far away (e.g. San Francisco ~550km from LA)
        StaffProfile profile = StaffProfile.builder()
                .userId(staffId)
                .homeLat(37.7749)
                .homeLon(-122.4194)
                .avgRating(new BigDecimal("5.00"))
                .skillTags(List.of())
                .reliabilityScore(BigDecimal.ONE)
                .build();

        Shift shift = Shift.builder()
                .siteLat(34.0522)
                .siteLon(-118.2437)
                .requiredSkills(List.of())
                .build();

        MatchingService.ScoredCandidate candidate = matchingService.score(profile, shift);

        // Proximity is 0.0, rating 1.0 * 0.25, skill 1.0 * 0.25, reliability 1.0 * 0.15 => 0.6500
        assertEquals(new BigDecimal("0.6500"), candidate.score());
        assertTrue(candidate.distanceMeters() > 500_000);
    }
}

