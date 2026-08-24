package com.vendorpulse.platform.scheduler;

import com.vendorpulse.platform.booking.entity.Booking;
import com.vendorpulse.platform.booking.entity.BookingStatus;
import com.vendorpulse.platform.booking.repository.BookingRepository;
import com.vendorpulse.platform.booking.service.MatchingService;
import com.vendorpulse.platform.config.NoShowProperties;
import com.vendorpulse.platform.event.KafkaEventPublisher;
import com.vendorpulse.platform.identity.entity.StaffProfile;
import com.vendorpulse.platform.identity.repository.StaffProfileRepository;
import com.vendorpulse.platform.notification.service.NotificationService;
import com.vendorpulse.platform.shift.entity.Shift;
import com.vendorpulse.platform.shift.entity.ShiftStatus;
import com.vendorpulse.platform.shift.repository.ShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Autonomous no-call/no-show handling — spec §4.1:
 *  1. Detect bookings 15+ minutes past shift start with no CLOCK_IN.
 *  2. Mark NO_SHOW, apply a multiplicative reliability penalty.
 *  3. Re-open the shift and immediately re-score the backup pool.
 *  4. After 3 no-shows in a rolling 90-day window, suspend pending Admin review.
 */
@Component
public class NoShowSweepJob {

    private static final Logger log = LoggerFactory.getLogger(NoShowSweepJob.class);
    private static final BigDecimal RELIABILITY_PENALTY_FACTOR = new BigDecimal("0.85");
    public static final String TOPIC_WORKER_NOSHOW = "worker.noshow";

    private final BookingRepository bookingRepository;
    private final ShiftRepository shiftRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final KafkaEventPublisher eventPublisher;
    private final NoShowProperties properties;

    public NoShowSweepJob(BookingRepository bookingRepository,
                           ShiftRepository shiftRepository,
                           StaffProfileRepository staffProfileRepository,
                           MatchingService matchingService,
                           NotificationService notificationService,
                           KafkaEventPublisher eventPublisher,
                           NoShowProperties properties) {
        this.bookingRepository = bookingRepository;
        this.shiftRepository = shiftRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.matchingService = matchingService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Scheduled(cron = "${vendorpulse.noshow.sweep-cron}")
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(properties.getGraceMinutes() * 60L);
        List<Booking> overdue = bookingRepository.findOverdueUnclockedBookings(cutoff);

        for (Booking booking : overdue) {
            try {
                handleNoShow(booking);
            } catch (Exception e) {
                log.error("Failed to process no-show for booking {}: {}", booking.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    void handleNoShow(Booking booking) {
        booking.setStatus(BookingStatus.NO_SHOW);
        bookingRepository.save(booking);

        Shift shift = shiftRepository.findById(booking.getShiftId()).orElseThrow();
        shift.setFilledCount(Math.max(0, shift.getFilledCount() - 1));
        shift.setStatus(ShiftStatus.MATCHING);
        shiftRepository.save(shift);

        StaffProfile profile = staffProfileRepository.findById(booking.getStaffId()).orElseThrow();
        profile.setReliabilityScore(profile.getReliabilityScore()
                .multiply(RELIABILITY_PENALTY_FACTOR).setScale(4, RoundingMode.HALF_EVEN));
        profile.setNoShowCount90d(profile.getNoShowCount90d() + 1);
        if (profile.getNoShowCount90d() >= properties.getSuspendAfterCount90d()) {
            profile.setSuspended(true);
            log.warn("Staff {} suspended after {} no-shows in 90 days", profile.getUserId(), profile.getNoShowCount90d());
        }
        staffProfileRepository.save(profile);

        eventPublisher.publish(TOPIC_WORKER_NOSHOW, booking.getId().toString(),
                Map.of("bookingId", booking.getId().toString(), "shiftId", shift.getId().toString(),
                        "staffId", booking.getStaffId().toString()));

        dispatchBackup(shift);
        notificationService.notifyOwnerOfNoShow(shift, booking);
    }

    private void dispatchBackup(Shift shift) {
        var candidates = matchingService.findEligibleCandidates(shift);
        if (candidates.isEmpty()) {
            log.warn("No backup candidates found for shift {} after no-show", shift.getId());
            return;
        }

        var top = candidates.get(0);
        Booking backupBooking = Booking.builder()
                .shiftId(shift.getId())
                .staffId(top.staffId())
                .status(BookingStatus.CLAIMED)
                .matchScore(top.score())
                .backupDispatch(true)
                .build();
        bookingRepository.save(backupBooking);

        shift.setFilledCount(shift.getFilledCount() + 1);
        if (shift.getFilledCount() >= shift.getHeadcount()) {
            shift.setStatus(ShiftStatus.FILLED);
        }
        shiftRepository.save(shift);

        notificationService.notifyStaffOfBackupDispatch(shift, backupBooking);
    }
}
