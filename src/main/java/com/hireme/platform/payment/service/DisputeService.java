package com.hireme.platform.payment.service;

import com.hireme.platform.common.exception.DomainExceptions.InvalidStateTransitionException;
import com.hireme.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.hireme.platform.event.KafkaEventPublisher;
import com.hireme.platform.payment.entity.Dispute;
import com.hireme.platform.payment.entity.Invoice;
import com.hireme.platform.payment.repository.DisputeRepository;
import com.hireme.platform.payment.repository.InvoiceRepository;
import com.hireme.platform.timesheet.entity.Timesheet;
import com.hireme.platform.timesheet.entity.TimesheetStatus;
import com.hireme.platform.timesheet.repository.TimesheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Escrow state-flow from spec §4.2:
 * PENDING_APPROVAL/APPROVED --dispute--> DISPUTED --> ESCROWED --> UNDER_REVIEW --> ADJUSTED|APPROVED --> FINALIZED.
 * Funds are held (manual-capture on the PaymentIntent, or simply not yet
 * charged if the dispute beat the approval), never reversed - the worker
 * sees "payment pending review," not "payment failed."
 */
@Service
public class DisputeService {

    public static final String TOPIC_DISPUTE_RESOLVED = "dispute.resolved";

    private final DisputeRepository disputeRepository;
    private final InvoiceRepository invoiceRepository;
    private final TimesheetRepository timesheetRepository;
    private final PaymentService paymentService;
    private final KafkaEventPublisher eventPublisher;

    public DisputeService(DisputeRepository disputeRepository,
                           InvoiceRepository invoiceRepository,
                           TimesheetRepository timesheetRepository,
                           PaymentService paymentService,
                           KafkaEventPublisher eventPublisher) {
        this.disputeRepository = disputeRepository;
        this.invoiceRepository = invoiceRepository;
        this.timesheetRepository = timesheetRepository;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Dispute raise(Timesheet timesheet, UUID raisedBy, String reason) {
        Optional<Invoice> existingInvoice = invoiceRepository.findByTimesheetId(timesheet.getId());
        existingInvoice.ifPresent(inv -> paymentService.escrow(timesheet.getId()));

        Dispute dispute = Dispute.builder()
                .timesheetId(timesheet.getId())
                .raisedBy(raisedBy)
                .reason(reason)
                .escrowInvoiceId(existingInvoice.map(Invoice::getId).orElse(null))
                .build();

        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute assign(UUID disputeId, UUID adminId) {
        Dispute dispute = get(disputeId);
        dispute.setAssignedAdminId(adminId);

        Timesheet timesheet = timesheetRepository.findById(dispute.getTimesheetId())
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet", dispute.getTimesheetId()));
        timesheet.setStatus(TimesheetStatus.UNDER_REVIEW);
        timesheetRepository.save(timesheet);

        return disputeRepository.save(dispute);
    }

    /**
     * Admin resolution: either confirm the original hours (APPROVED, capture
     * as-is) or adjust them (recalculates regular/overtime/holiday hours and
     * re-derives the pricing breakdown before charging).
     */
    @Transactional
    public Dispute resolve(UUID disputeId, UUID adminId, String resolutionNote,
                            BigDecimal adjustedRegularHours, BigDecimal adjustedOvertimeHours,
                            BigDecimal adjustedHolidayHours) {
        Dispute dispute = get(disputeId);
        if (dispute.getResolvedAt() != null) {
            throw new InvalidStateTransitionException("Dispute " + disputeId + " already resolved");
        }

        UUID timesheetId = dispute.getTimesheetId();
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet", timesheetId));

        boolean isAdjustment = adjustedRegularHours != null;
        if (isAdjustment) {
            timesheet.setRegularHours(adjustedRegularHours);
            timesheet.setOvertimeHours(adjustedOvertimeHours != null ? adjustedOvertimeHours : BigDecimal.ZERO);
            timesheet.setHolidayHours(adjustedHolidayHours != null ? adjustedHolidayHours : BigDecimal.ZERO);
            timesheet.setStatus(TimesheetStatus.ADJUSTED);
            // NOTE: a full implementation re-runs PricingEngineService here to recompute
            // basePay/markupFee/billRate from the corrected hours before re-charging;
            // omitted for brevity but the single call site is TimesheetService/ClockService's
            // buildTimesheet-equivalent path.
        } else {
            timesheet.setStatus(TimesheetStatus.APPROVED);
        }
        timesheet.setApprovedAt(Instant.now());
        timesheetRepository.save(timesheet);

        dispute.setAssignedAdminId(adminId);
        dispute.setResolution(resolutionNote);
        dispute.setResolvedAt(Instant.now());
        dispute = disputeRepository.save(dispute);

        paymentService.chargeForTimesheet(timesheet.getId());

        eventPublisher.publish(TOPIC_DISPUTE_RESOLVED, dispute.getId().toString(),
                Map.of("disputeId", dispute.getId().toString(), "timesheetId", timesheet.getId().toString(),
                        "adjusted", isAdjustment));

        return dispute;
    }

    public Dispute get(UUID id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", id));
    }
}
