package com.hireme.platform.identity.repository;

import com.hireme.platform.identity.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {

    /**
     * Cold-start / reconciliation candidate query used by the matching engine
     * when the Redis geo hot-path (GEOSEARCH on staff:geo:available) needs a
     * source-of-truth cross-check. Distance filter uses the Haversine formula
     * directly in Java on the returned candidate set (see MatchingService) -
     * this query only narrows by a coarse bounding box + eligibility flags to
     * keep the DB round trip cheap.
     */
    @Query("""
            select sp from StaffProfile sp
            where sp.suspended = false
              and sp.backgroundCheckStatus = 'CLEAR'
              and sp.homeLat between :minLat and :maxLat
              and sp.homeLon between :minLon and :maxLon
            """)
    List<StaffProfile> findEligibleCandidatesInBoundingBox(double minLat, double maxLat, double minLon, double maxLon);
}
