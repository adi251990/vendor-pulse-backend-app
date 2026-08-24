package com.hireme.platform.booking.repository;

import com.hireme.platform.booking.entity.Booking;
import com.hireme.platform.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByStaffIdAndStatus(UUID staffId, BookingStatus status);

    Optional<Booking> findByShiftIdAndStaffId(UUID shiftId, UUID staffId);

    List<Booking> findByShiftId(UUID shiftId);

    /**
     * Bookings whose shift has started more than {@code graceMinutes} ago
     * but that never produced a CLOCK_IN geo_log — the no-show sweep query
     * (spec §4.1). Kept here rather than a native query so it stays portable
     * if the persistence layer changes.
     */
    @org.springframework.data.jpa.repository.Query("""
            select b from Booking b
            join com.hireme.platform.shift.entity.Shift s on s.id = b.shiftId
            where b.status = 'CLAIMED'
              and s.startTime < :cutoff
              and not exists (
                  select 1 from com.hireme.platform.attendance.entity.GeoLog g
                  where g.bookingId = b.id and g.eventType = 'CLOCK_IN'
              )
            """)
    List<Booking> findOverdueUnclockedBookings(java.time.Instant cutoff);
}
