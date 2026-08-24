package com.vendorpulse.platform.booking.service;

import com.vendorpulse.platform.booking.dto.ClaimResponse;
import com.vendorpulse.platform.booking.entity.Booking;
import com.vendorpulse.platform.booking.entity.BookingStatus;
import com.vendorpulse.platform.booking.repository.BookingRepository;
import com.vendorpulse.platform.common.exception.DomainExceptions.ClaimFailedException;
import com.vendorpulse.platform.common.exception.DomainExceptions.DuplicateResourceException;
import com.vendorpulse.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.vendorpulse.platform.common.exception.DomainExceptions.ShiftAlreadyClaimedException;
import com.vendorpulse.platform.common.exception.DomainExceptions.ShiftNotClaimableException;
import com.vendorpulse.platform.common.exception.DomainExceptions.StaffNotEligibleException;
import com.vendorpulse.platform.identity.entity.StaffProfile;
import com.vendorpulse.platform.identity.repository.StaffProfileRepository;
import com.vendorpulse.platform.shift.entity.Shift;
import com.vendorpulse.platform.shift.repository.ShiftRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Solves the "race condition" problem from spec §2.B: thousands of Android
 * clients tapping "claim" on the same popular shift at once must resolve to
 * exactly one winner.
 *
 * Two independent safety nets:
 *  1. A Redisson distributed lock (fail-fast, no queueing) around the
 *     claim-eligibility check + insert, so under normal operation only one
 *     request per shift ever reaches the DB write at a time.
 *  2. An optimistic-lock (@Version on Shift) as a backstop against a
 *     Redis/Redisson split-brain or lock-server outage - if two transactions
 *     somehow both got past the lock, only one commits; the other gets an
 *     ObjectOptimisticLockingFailureException, which we translate into the
 *     same 409 SHIFT_ALREADY_CLAIMED the client already knows how to handle.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);
    private static final long LOCK_WAIT_SECONDS = 0; // fail fast, never queue behind another claimant
    private static final long LOCK_LEASE_SECONDS = 3;

    private final RedissonClient redissonClient;
    private final ShiftRepository shiftRepository;
    private final BookingRepository bookingRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final MatchingService matchingService;
    private final TransactionTemplate transactionTemplate;

    public ClaimService(RedissonClient redissonClient,
                         ShiftRepository shiftRepository,
                         BookingRepository bookingRepository,
                         StaffProfileRepository staffProfileRepository,
                         MatchingService matchingService,
                         TransactionTemplate transactionTemplate) {
        this.redissonClient = redissonClient;
        this.shiftRepository = shiftRepository;
        this.bookingRepository = bookingRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.matchingService = matchingService;
        this.transactionTemplate = transactionTemplate;
    }

    public ClaimResponse claim(UUID shiftId, UUID staffId) {
        RLock lock = redissonClient.getLock("lock:booking:claim:" + shiftId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                // Someone else holds the lock right now => treat as already-claimed
                // from the caller's perspective rather than making them wait.
                throw new ShiftAlreadyClaimedException(shiftId);
            }

            try {
                return transactionTemplate.execute(status -> claimInTransaction(shiftId, staffId));
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.info("Optimistic lock lost the race for shift {} (staff {})", shiftId, staffId);
                throw new ShiftAlreadyClaimedException(shiftId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaimFailedException(shiftId, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private ClaimResponse claimInTransaction(UUID shiftId, UUID staffId) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId));

        if (!shift.isClaimable()) {
            throw new ShiftNotClaimableException("Shift " + shiftId + " is no longer open for claims (status="
                    + shift.getStatus() + ", filled=" + shift.getFilledCount() + "/" + shift.getHeadcount() + ")");
        }

        bookingRepository.findByShiftIdAndStaffId(shiftId, staffId).ifPresent(existing -> {
            throw new DuplicateResourceException("You have already claimed this shift");
        });

        StaffProfile profile = staffProfileRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", staffId));

        if (profile.isSuspended()) {
            throw new StaffNotEligibleException("Account is suspended pending review");
        }
        if (!"CLEAR".equals(profile.getBackgroundCheckStatus())) {
            throw new StaffNotEligibleException("Background check status must be CLEAR to claim shifts");
        }

        var scored = matchingService.score(profile, shift);

        Booking booking = Booking.builder()
                .shiftId(shiftId)
                .staffId(staffId)
                .status(BookingStatus.CLAIMED)
                .matchScore(scored.score())
                .backupDispatch(false)
                .build();
        booking = bookingRepository.save(booking);

        shift.setFilledCount(shift.getFilledCount() + 1);
        if (shift.getFilledCount() >= shift.getHeadcount()) {
            shift.setStatus(com.vendorpulse.platform.shift.entity.ShiftStatus.FILLED);
        } else {
            shift.setStatus(com.vendorpulse.platform.shift.entity.ShiftStatus.MATCHING);
        }
        shiftRepository.save(shift); // flush triggers the @Version check

        return new ClaimResponse(booking.getId(), shiftId, booking.getStatus().name(), scored.score());
    }
}
