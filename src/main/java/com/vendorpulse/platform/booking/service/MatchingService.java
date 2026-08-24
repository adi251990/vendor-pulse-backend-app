package com.vendorpulse.platform.booking.service;

import com.vendorpulse.platform.common.util.GeoUtils;
import com.vendorpulse.platform.config.MatchingProperties;
import com.vendorpulse.platform.identity.entity.StaffProfile;
import com.vendorpulse.platform.identity.repository.StaffProfileRepository;
import com.vendorpulse.platform.shift.entity.Shift;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scoring engine from spec §2.B:
 * <pre>
 * match_score = w1*proximity_score + w2*rating_score + w3*skill_match_score + w4*reliability_score
 * </pre>
 * This implementation queries Postgres directly (cold-start / reconciliation
 * path). In production the hot path for the 9AM dispatch burst is a Redis
 * GEOSEARCH against a `staff:geo:available` sorted set updated on every
 * availability/location ping — see the architecture notes in the system
 * spec §2.B; wiring that in is a drop-in replacement for
 * {@link #findEligibleCandidates} that keeps the same scoring math below.
 */
@Service
public class MatchingService {

    private final StaffProfileRepository staffProfileRepository;
    private final MatchingProperties matchingProperties;

    public MatchingService(StaffProfileRepository staffProfileRepository,
                            MatchingProperties matchingProperties) {
        this.staffProfileRepository = staffProfileRepository;
        this.matchingProperties = matchingProperties;
    }

    public List<ScoredCandidate> findEligibleCandidates(Shift shift) {
        var box = GeoUtils.boundingBox(shift.getSiteLat(), shift.getSiteLon(), matchingProperties.getDefaultRadiusKm());
        List<StaffProfile> candidates = staffProfileRepository
                .findEligibleCandidatesInBoundingBox(box.minLat(), box.maxLat(), box.minLon(), box.maxLon());

        return candidates.stream()
                .map(sp -> score(sp, shift))
                .filter(c -> c.distanceMeters() <= matchingProperties.getDefaultRadiusKm() * 1000)
                .sorted(Comparator.comparing(ScoredCandidate::score).reversed())
                .toList();
    }

    public ScoredCandidate score(StaffProfile profile, Shift shift) {
        double distanceMeters = (profile.getHomeLat() != null && profile.getHomeLon() != null)
                ? GeoUtils.distanceMeters(profile.getHomeLat(), profile.getHomeLon(), shift.getSiteLat(), shift.getSiteLon())
                : Double.MAX_VALUE;

        double proximityScore = Math.max(0.0, 1.0 - (distanceMeters / (matchingProperties.getDefaultRadiusKm() * 1000)));
        double ratingScore = profile.getAvgRating().doubleValue() / 5.0;
        double skillScore = jaccardSimilarity(shift.getRequiredSkills(), profile.getSkillTags());
        double reliabilityScore = profile.getReliabilityScore().doubleValue();

        var w = matchingProperties.getWeights();
        double total = w.getProximity() * proximityScore
                + w.getRating() * ratingScore
                + w.getSkill() * skillScore
                + w.getReliability() * reliabilityScore;

        BigDecimal score = BigDecimal.valueOf(total).setScale(4, RoundingMode.HALF_EVEN);
        return new ScoredCandidate(profile.getUserId(), score, distanceMeters);
    }

    private double jaccardSimilarity(List<String> required, List<String> held) {
        if (required == null || required.isEmpty()) {
            return 1.0; // no skill requirement => full match
        }
        Set<String> a = new HashSet<>(required);
        Set<String> b = new HashSet<>(held != null ? held : List.of());
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    public record ScoredCandidate(java.util.UUID staffId, BigDecimal score, double distanceMeters) {
    }
}
