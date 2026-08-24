package com.hireme.platform.scheduler;

import com.hireme.platform.booking.service.MatchingService;
import com.hireme.platform.identity.repository.UserRepository;
import com.hireme.platform.notification.service.NotificationService;
import com.hireme.platform.shift.entity.Shift;
import com.hireme.platform.shift.entity.ShiftStatus;
import com.hireme.platform.shift.repository.ShiftRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Urgent-unfilled-shift escalation cascade — spec §2.B. Runs every minute
 * and buckets each open shift by minutes-to-start, firing the matching
 * escalation tier. Idempotency against duplicate sends within the same
 * bucket is intentionally left simple (minute-granularity cron + narrow
 * bucket windows below) rather than adding per-shift "last notified at"
 * state, which would be the natural next iteration for production.
 */
@Component
public class NotificationCascadeJob {

    private final ShiftRepository shiftRepository;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationCascadeJob(ShiftRepository shiftRepository,
                                   MatchingService matchingService,
                                   NotificationService notificationService,
                                   UserRepository userRepository) {
        this.shiftRepository = shiftRepository;
        this.matchingService = matchingService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0 * * * * *")
    public void runCascade() {
        Instant now = Instant.now();
        List<Shift> openShifts = shiftRepository.findByStatusInOrderByStartTimeAsc(
                List.of(ShiftStatus.PUBLISHED, ShiftStatus.MATCHING));

        for (Shift shift : openShifts) {
            long minutesToStart = Duration.between(now, shift.getStartTime()).toMinutes();
            NotificationService.CascadeLevel level = bucketFor(minutesToStart);
            if (level == null) {
                continue;
            }

            var candidatePhones = matchingService.findEligibleCandidates(shift).stream()
                    .limit(25)
                    .map(c -> userRepository.findById(c.staffId()).map(u -> u.getPhone()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            notificationService.escalate(shift, level, candidatePhones);
        }
    }

    private NotificationService.CascadeLevel bucketFor(long minutesToStart) {
        if (minutesToStart <= 31 && minutesToStart >= 29) return NotificationService.CascadeLevel.T_MINUS_30MIN;
        if (minutesToStart <= 61 && minutesToStart >= 59) return NotificationService.CascadeLevel.T_MINUS_60MIN;
        if (minutesToStart <= 91 && minutesToStart >= 89) return NotificationService.CascadeLevel.T_MINUS_90MIN;
        if (minutesToStart <= 121 && minutesToStart >= 119) return NotificationService.CascadeLevel.T_MINUS_2H;
        return null;
    }
}
