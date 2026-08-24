package com.vendorpulse.platform.timesheet.repository;

import com.vendorpulse.platform.timesheet.entity.Timesheet;
import com.vendorpulse.platform.timesheet.entity.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {
    Optional<Timesheet> findByBookingId(UUID bookingId);

    List<Timesheet> findByStatusAndApprovedAtBefore(TimesheetStatus status, Instant cutoff);

    List<Timesheet> findByStatus(TimesheetStatus status);
}
