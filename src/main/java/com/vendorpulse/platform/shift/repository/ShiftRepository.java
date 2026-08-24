package com.vendorpulse.platform.shift.repository;

import com.vendorpulse.platform.shift.entity.Shift;
import com.vendorpulse.platform.shift.entity.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findByStatusInOrderByStartTimeAsc(List<ShiftStatus> statuses);

    List<Shift> findByOrgId(UUID orgId);

    /**
     * Row-level lock used inside the Redisson-guarded claim transaction
     * (ClaimService) so a lock-server split-brain can't double-book a shift -
     * see spec §2.B "race condition" design.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.id = :id")
    Optional<Shift> findByIdForUpdate(UUID id);

    @Query("""
            select s from Shift s
            where s.status in ('PUBLISHED', 'MATCHING')
              and s.filledCount < s.headcount
              and s.startTime > :now
            order by s.startTime asc
            """)
    List<Shift> findOpenFeed(Instant now);
}
