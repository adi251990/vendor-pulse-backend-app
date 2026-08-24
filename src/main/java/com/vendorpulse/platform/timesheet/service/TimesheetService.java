package com.vendorpulse.platform.timesheet.service;

import com.vendorpulse.platform.common.exception.DomainExceptions.InvalidStateTransitionException;
import com.vendorpulse.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.vendorpulse.platform.event.KafkaEventPublisher;
import com.vendorpulse.platform.payment.service.DisputeService;
import com.vendorpulse.platform.timesheet.entity.Timesheet;
import com.vendorpulse.platform.timesheet.entity.TimesheetStatus;
import com.vendorpulse.platform.timesheet.repository.TimesheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the PENDING_APPROVAL -> APPROVED / DISPUTED transitions from spec
 * §2.A and §4.2. Approval publishes {@code timesheet.approved}, which
 * PaymentService listens for to kick off the Stripe Connect charge - the
 * two modules never call each other directly, keeping payments free to move
 * to its own deployable later without touching this class.
 */
@Service
public class TimesheetService {

    public static final String TOPIC_TIMESHEET_APPROVED = "timesheet.approved";
    public static final String TOPIC_TIMESHEET_DISPUTED = "timesheet.disputed";

    private final TimesheetRepository timesheetRepository;
    private final DisputeService disputeService;
    private final KafkaEventPublisher eventPublisher;

    public TimesheetService(TimesheetRepository timesheetRepository,
                             DisputeService disputeService,
                             KafkaEventPublisher eventPublisher) {
        this.timesheetRepository = timesheetRepository;
        this.disputeService = disputeService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Timesheet approve(UUID timesheetId) {
        Timesheet timesheet = get(timesheetId);
        if (timesheet.getStatus() != TimesheetStatus.PENDING_APPROVAL
                && timesheet.getStatus() != TimesheetStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "Cannot approve timesheet in status " + timesheet.getStatus());
        }

        timesheet.setStatus(TimesheetStatus.APPROVED);
        timesheet.setApprovedAt(Instant.now());
        timesheet = timesheetRepository.save(timesheet);

        eventPublisher.publish(TOPIC_TIMESHEET_APPROVED, timesheet.getId().toString(),
                Map.of("timesheetId", timesheet.getId().toString(),
                        "vendorBillRate", timesheet.getVendorBillRate(),
                        "platformMarkupFee", timesheet.getPlatformMarkupFee()));

        return timesheet;
    }

    @Transactional
    public Timesheet dispute(UUID timesheetId, UUID raisedByUserId, String reason) {
        Timesheet timesheet = get(timesheetId);
        if (timesheet.getStatus() != TimesheetStatus.PENDING_APPROVAL
                && timesheet.getStatus() != TimesheetStatus.APPROVED) {
            throw new InvalidStateTransitionException(
                    "Cannot dispute timesheet in status " + timesheet.getStatus());
        }

        timesheet.setStatus(TimesheetStatus.DISPUTED);
        timesheet.setDisputeReason(reason);
        timesheet = timesheetRepository.save(timesheet);

        disputeService.raise(timesheet, raisedByUserId, reason);

        eventPublisher.publish(TOPIC_TIMESHEET_DISPUTED, timesheet.getId().toString(),
                Map.of("timesheetId", timesheet.getId().toString(), "reason", reason));

        return timesheet;
    }

    /** Auto-approval sweep: PENDING_APPROVAL timesheets past the 48h owner-review SLA (spec §2.A). */
    @Transactional
    public int autoApproveOverdue(Instant cutoff) {
        var overdue = timesheetRepository.findByStatus(TimesheetStatus.PENDING_APPROVAL).stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(cutoff))
                .toList();
        overdue.forEach(t -> approve(t.getId()));
        return overdue.size();
    }

    public Timesheet get(UUID id) {
        return timesheetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet", id));
    }
}
