package com.hireme.platform.payment.service;

import com.hireme.platform.booking.entity.Booking;
import com.hireme.platform.booking.repository.BookingRepository;
import com.hireme.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.hireme.platform.event.KafkaEventPublisher;
import com.hireme.platform.identity.entity.StaffProfile;
import com.hireme.platform.identity.entity.Vendor;
import com.hireme.platform.identity.repository.StaffProfileRepository;
import com.hireme.platform.identity.repository.VendorRepository;
import com.hireme.platform.payment.entity.Invoice;
import com.hireme.platform.payment.entity.InvoiceStatus;
import com.hireme.platform.payment.repository.InvoiceRepository;
import com.hireme.platform.shift.entity.Shift;
import com.hireme.platform.shift.repository.ShiftRepository;
import com.hireme.platform.timesheet.entity.Timesheet;
import com.hireme.platform.timesheet.entity.TimesheetStatus;
import com.hireme.platform.timesheet.repository.TimesheetRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Split-payment / automated invoicing workflow — spec §2.D. Uses a Stripe
 * Connect *destination charge*: one PaymentIntent both charges the Owner's
 * saved payment method and (via {@code transfer_data.destination} +
 * {@code application_fee_amount}) routes net pay to the Staff member's
 * connected account and the markup to the platform, in a single API call -
 * no separate settlement/reconciliation step needed.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final String TOPIC_PAYMENT_COMPLETED = "payment.completed";
    public static final String TOPIC_PAYMENT_FAILED = "payment.failed";

    private final TimesheetRepository timesheetRepository;
    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final ShiftRepository shiftRepository;
    private final VendorRepository vendorRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final KafkaEventPublisher eventPublisher;

    public PaymentService(TimesheetRepository timesheetRepository,
                           InvoiceRepository invoiceRepository,
                           BookingRepository bookingRepository,
                           ShiftRepository shiftRepository,
                           VendorRepository vendorRepository,
                           StaffProfileRepository staffProfileRepository,
                           KafkaEventPublisher eventPublisher) {
        this.timesheetRepository = timesheetRepository;
        this.invoiceRepository = invoiceRepository;
        this.bookingRepository = bookingRepository;
        this.shiftRepository = shiftRepository;
        this.vendorRepository = vendorRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Listens for {@code timesheet.approved} (published by TimesheetService)
     * and charges the vendor. Decoupled via Kafka rather than a direct
     * service call so payments can be split into its own deployable later
     * without TimesheetService needing to change.
     */
    @KafkaListener(topics = "timesheet.approved", groupId = "hireme-backend-payments")
    public void onTimesheetApproved(Map<String, Object> payload) {
        UUID timesheetId = UUID.fromString((String) payload.get("timesheetId"));
        try {
            chargeForTimesheet(timesheetId);
        } catch (Exception e) {
            log.error("Payment processing failed for timesheet {}: {}", timesheetId, e.getMessage(), e);
            eventPublisher.publish(TOPIC_PAYMENT_FAILED, timesheetId.toString(),
                    Map.of("timesheetId", timesheetId.toString(), "reason", String.valueOf(e.getMessage())));
        }
    }

    @Transactional
    public Invoice chargeForTimesheet(UUID timesheetId) {
        if (invoiceRepository.findByTimesheetId(timesheetId).isPresent()) {
            return invoiceRepository.findByTimesheetId(timesheetId).get(); // idempotent retry
        }

        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet", timesheetId));

        Booking booking = bookingRepository.findById(timesheet.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", timesheet.getBookingId()));
        Shift shift = shiftRepository.findById(booking.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", booking.getShiftId()));
        Vendor vendor = vendorRepository.findById(shift.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", shift.getOrgId()));
        StaffProfile staff = staffProfileRepository.findById(booking.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", booking.getStaffId()));

        Invoice invoice = Invoice.builder()
                .timesheetId(timesheetId)
                .amountCharged(timesheet.getVendorBillRate())
                .platformFee(timesheet.getPlatformMarkupFee())
                .workerNetPay(timesheet.getVendorBillRate().subtract(timesheet.getPlatformMarkupFee()))
                .status(InvoiceStatus.PENDING)
                .build();

        try {
            PaymentIntent intent = createDestinationCharge(vendor, staff, invoice);
            invoice.setStripePaymentIntentId(intent.getId());
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(Instant.now());
        } catch (StripeException e) {
            invoice.setStatus(InvoiceStatus.FAILED);
            log.warn("Stripe charge failed for timesheet {}: {}", timesheetId, e.getMessage());
        }

        invoice = invoiceRepository.save(invoice);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            timesheet.setStatus(TimesheetStatus.FINALIZED);
            timesheetRepository.save(timesheet);
            eventPublisher.publish(TOPIC_PAYMENT_COMPLETED, invoice.getId().toString(),
                    Map.of("invoiceId", invoice.getId().toString(), "timesheetId", timesheetId.toString(),
                            "workerNetPay", invoice.getWorkerNetPay()));
        } else {
            eventPublisher.publish(TOPIC_PAYMENT_FAILED, timesheetId.toString(),
                    Map.of("timesheetId", timesheetId.toString(), "reason", "stripe_charge_failed"));
        }

        return invoice;
    }

    private PaymentIntent createDestinationCharge(Vendor vendor, StaffProfile staff, Invoice invoice) throws StripeException {
        long amountCents = toCents(invoice.getAmountCharged());
        long feeCents = toCents(invoice.getPlatformFee());

        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .setCustomer(vendor.getStripeCustomerId())
                .setConfirm(true)
                .setOffSession(true)
                .setApplicationFeeAmount(feeCents)
                .setTransferData(
                        PaymentIntentCreateParams.TransferData.builder()
                                .setDestination(staff.getStripeAccountId())
                                .build());

        return PaymentIntent.create(params.build());
    }

    /** Escrow: flip the PaymentIntent to manual-capture so funds are held, not reversed, while a dispute is reviewed (spec §4.2). */
    @Transactional
    public void escrow(UUID timesheetId) {
        invoiceRepository.findByTimesheetId(timesheetId).ifPresentOrElse(invoice -> {
            invoice.setStatus(InvoiceStatus.ESCROWED);
            invoiceRepository.save(invoice);
        }, () -> log.info("No invoice yet for disputed timesheet {} - nothing to escrow (not yet charged)", timesheetId));
    }

    private long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }
}
