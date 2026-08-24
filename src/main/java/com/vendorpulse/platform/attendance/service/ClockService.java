package com.vendorpulse.platform.attendance.service;

import com.vendorpulse.platform.attendance.dto.ClockEventRequest;
import com.vendorpulse.platform.attendance.dto.ClockEventResponse;
import com.vendorpulse.platform.attendance.entity.GeoEventType;
import com.vendorpulse.platform.attendance.entity.GeoLog;
import com.vendorpulse.platform.attendance.repository.GeoLogRepository;
import com.vendorpulse.platform.booking.entity.Booking;
import com.vendorpulse.platform.booking.entity.BookingStatus;
import com.vendorpulse.platform.booking.repository.BookingRepository;
import com.vendorpulse.platform.common.exception.DomainExceptions.AlreadyClockedInException;
import com.vendorpulse.platform.common.exception.DomainExceptions.IdentityVerificationFailedException;
import com.vendorpulse.platform.common.exception.DomainExceptions.InvalidSignatureException;
import com.vendorpulse.platform.common.exception.DomainExceptions.OutsideGeofenceException;
import com.vendorpulse.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.vendorpulse.platform.common.util.HolidayCalendar;
import com.vendorpulse.platform.config.GeofenceProperties;
import com.vendorpulse.platform.config.PricingProperties;
import com.vendorpulse.platform.identity.repository.VendorRepository;
import com.vendorpulse.platform.shift.entity.Shift;
import com.vendorpulse.platform.shift.repository.ShiftRepository;
import com.vendorpulse.platform.shift.service.PricingEngineService;
import com.vendorpulse.platform.shift.service.ShiftPricingContext;
import com.vendorpulse.platform.shift.service.SurgeCalculator;
import com.vendorpulse.platform.timesheet.entity.Timesheet;
import com.vendorpulse.platform.timesheet.entity.TimesheetStatus;
import com.vendorpulse.platform.timesheet.repository.TimesheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class ClockService {

    private final GeoLogRepository geoLogRepository;
    private final BookingRepository bookingRepository;
    private final ShiftRepository shiftRepository;
    private final TimesheetRepository timesheetRepository;
    private final VendorRepository vendorRepository;
    private final GeofenceService geofenceService;
    private final DeviceSignatureVerifier signatureVerifier;
    private final GeofenceProperties geofenceProperties;
    private final PricingProperties pricingProperties;
    private final PricingEngineService pricingEngineService;
    private final SurgeCalculator surgeCalculator;
    private final HolidayCalendar holidayCalendar;

    public ClockService(GeoLogRepository geoLogRepository,
                         BookingRepository bookingRepository,
                         ShiftRepository shiftRepository,
                         TimesheetRepository timesheetRepository,
                         VendorRepository vendorRepository,
                         GeofenceService geofenceService,
                         DeviceSignatureVerifier signatureVerifier,
                         GeofenceProperties geofenceProperties,
                         PricingProperties pricingProperties,
                         PricingEngineService pricingEngineService,
                         SurgeCalculator surgeCalculator,
                         HolidayCalendar holidayCalendar) {
        this.geoLogRepository = geoLogRepository;
        this.bookingRepository = bookingRepository;
        this.shiftRepository = shiftRepository;
        this.timesheetRepository = timesheetRepository;
        this.vendorRepository = vendorRepository;
        this.geofenceService = geofenceService;
        this.signatureVerifier = signatureVerifier;
        this.geofenceProperties = geofenceProperties;
        this.pricingProperties = pricingProperties;
        this.pricingEngineService = pricingEngineService;
        this.surgeCalculator = surgeCalculator;
        this.holidayCalendar = holidayCalendar;
    }

    @Transactional
    public ClockEventResponse clockIn(ClockEventRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.bookingId()));

        if (geoLogRepository.existsByBookingIdAndEventType(booking.getId(), GeoEventType.CLOCK_IN)) {
            throw new AlreadyClockedInException();
        }

        Shift shift = shiftRepository.findById(booking.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", booking.getShiftId()));

        GeoLog log = validateAndBuildLog(request, shift, GeoEventType.CLOCK_IN);
        log = geoLogRepository.save(log);

        booking.setStatus(BookingStatus.ACTIVE);
        bookingRepository.save(booking);

        return toResponse(log, booking.getStatus(), request.recordedAt());
    }

    @Transactional
    public ClockEventResponse clockOut(ClockEventRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.bookingId()));

        Shift shift = shiftRepository.findById(booking.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", booking.getShiftId()));

        GeoLog clockInLog = geoLogRepository
                .findFirstByBookingIdAndEventTypeOrderByRecordedAtDesc(booking.getId(), GeoEventType.CLOCK_IN)
                .orElseThrow(() -> new ResourceNotFoundException("CLOCK_IN GeoLog for booking", booking.getId()));

        GeoLog clockOutLog = validateAndBuildLog(request, shift, GeoEventType.CLOCK_OUT);
        clockOutLog = geoLogRepository.save(clockOutLog);

        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        Timesheet timesheet = buildTimesheet(booking, shift, clockInLog, clockOutLog);
        timesheetRepository.save(timesheet);

        return toResponse(clockOutLog, booking.getStatus(), request.recordedAt());
    }

    private GeoLog validateAndBuildLog(ClockEventRequest request, Shift shift, GeoEventType eventType) {
        String canonical = signatureVerifier.canonicalize(
                request.bookingId().toString(), eventType.name(),
                request.recordedAt().toString(), request.location().lat(), request.location().lon());

        if (!signatureVerifier.verify(canonical, request.signature())) {
            throw new InvalidSignatureException();
        }

        boolean deviceOk = request.deviceAttestationToken() != null && !request.deviceAttestationToken().isBlank();
        // NOTE: a production build calls the Play Integrity API here to get a real verdict;
        // this scaffold only checks token presence and relies on the signature check above
        // as the primary tamper-resistance mechanism.

        boolean mockLocationSuspected = false; // would come from the Play Integrity verdict in production

        var geofenceCheck = geofenceService.check(shift, request.location().lat(), request.location().lon(),
                request.location().accuracyM());

        if (!geofenceCheck.withinGeofence()) {
            throw new OutsideGeofenceException(geofenceCheck.distanceMeters());
        }

        Double selfieScore = request.selfieVerificationScore();
        if (selfieScore != null && selfieScore < geofenceProperties.getSelfieVerificationThreshold()) {
            throw new IdentityVerificationFailedException(selfieScore);
        }

        return GeoLog.builder()
                .bookingId(request.bookingId())
                .eventType(eventType)
                .recordedAt(request.recordedAt())
                .lat(request.location().lat())
                .lon(request.location().lon())
                .accuracyM(BigDecimal.valueOf(request.location().accuracyM()))
                .withinGeofence(true)
                .deviceAttestationOk(deviceOk)
                .mockLocationSuspected(mockLocationSuspected)
                .selfieVerificationScore(selfieScore != null ? BigDecimal.valueOf(selfieScore) : null)
                .signature(request.signature())
                .build();
    }

    private ClockEventResponse toResponse(GeoLog log, BookingStatus bookingStatus, Instant recordedAt) {
        String syncStatus = Duration.between(recordedAt, Instant.now()).toHours()
                > geofenceProperties.getOfflineSyncGraceHours()
                ? "PENDING_LATE_SYNC_REVIEW"
                : "ACCEPTED";

        return new ClockEventResponse(
                log.getId(),
                log.isWithinGeofence(),
                bookingStatus.name(),
                log.getSelfieVerificationScore() != null ? log.getSelfieVerificationScore().doubleValue() : null,
                syncStatus);
    }

    /**
     * Computes regular/overtime/holiday hours and the full pricing breakdown
     * for one completed shift (spec §2.A). Overtime here is evaluated against
     * the daily threshold for this single shift only; true weekly overtime
     * aggregation across all of a worker's shifts platform-wide (see the
     * spec's note on FLSA-style obligations) requires summing hours across
     * every timesheet for the worker in the current ISO week and is left as
     * a follow-up - flagged here rather than silently ignored.
     */
    private Timesheet buildTimesheet(Booking booking, Shift shift, GeoLog clockIn, GeoLog clockOut) {
        double workedHoursRaw = Duration.between(clockIn.getRecordedAt(), clockOut.getRecordedAt()).toMinutes() / 60.0;
        BigDecimal workedHours = BigDecimal.valueOf(workedHoursRaw).setScale(2, RoundingMode.HALF_EVEN);

        boolean isHoliday = holidayCalendar.isHoliday(clockIn.getRecordedAt().atZone(ZoneOffset.UTC).toLocalDate());

        BigDecimal holidayHours = isHoliday ? workedHours : BigDecimal.ZERO;
        BigDecimal nonHolidayHours = isHoliday ? BigDecimal.ZERO : workedHours;

        BigDecimal dailyThreshold = pricingProperties.getDailyOvertimeThresholdHours();
        BigDecimal overtimeHours = nonHolidayHours.subtract(dailyThreshold).max(BigDecimal.ZERO);
        BigDecimal regularHours = nonHolidayHours.subtract(overtimeHours);

        BigDecimal markupPct = vendorRepository.findById(shift.getOrgId())
                .map(v -> v.getMarkupPctDefault())
                .orElse(new BigDecimal("0.22"));

        BigDecimal surge = surgeCalculator.multiplierFor(shift, clockIn.getRecordedAt());

        var ctx = new ShiftPricingContext(shift.getOrgId(), shift.getId(), shift.getHourlyRate(),
                regularHours, overtimeHours, holidayHours, markupPct);
        var pricing = pricingEngineService.calculate(ctx, surge);

        return Timesheet.builder()
                .bookingId(booking.getId())
                .clockInLogId(clockIn.getId())
                .clockOutLogId(clockOut.getId())
                .regularHours(regularHours)
                .overtimeHours(overtimeHours)
                .holidayHours(holidayHours)
                .baseWorkerPay(pricing.basePay())
                .platformMarkupFee(pricing.markupFee())
                .vendorBillRate(pricing.billRate())
                .status(TimesheetStatus.PENDING_APPROVAL)
                .build();
    }
}
