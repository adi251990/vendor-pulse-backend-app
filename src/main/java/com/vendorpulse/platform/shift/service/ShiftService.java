package com.vendorpulse.platform.shift.service;

import com.vendorpulse.platform.common.exception.DomainExceptions.NoPaymentMethodException;
import com.vendorpulse.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.vendorpulse.platform.identity.entity.Vendor;
import com.vendorpulse.platform.identity.repository.VendorRepository;
import com.vendorpulse.platform.shift.dto.CreateShiftRequest;
import com.vendorpulse.platform.shift.dto.ShiftResponse;
import com.vendorpulse.platform.shift.entity.Shift;
import com.vendorpulse.platform.shift.entity.ShiftStatus;
import com.vendorpulse.platform.shift.repository.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final VendorRepository vendorRepository;
    private final PricingEngineService pricingEngineService;
    private final SurgeCalculator surgeCalculator;

    public ShiftService(ShiftRepository shiftRepository,
                         VendorRepository vendorRepository,
                         PricingEngineService pricingEngineService,
                         SurgeCalculator surgeCalculator) {
        this.shiftRepository = shiftRepository;
        this.vendorRepository = vendorRepository;
        this.pricingEngineService = pricingEngineService;
        this.surgeCalculator = surgeCalculator;
    }

    @Transactional
    public ShiftResponse createDraft(UUID orgId, CreateShiftRequest request) {
        Vendor vendor = vendorRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", orgId));

        if (vendor.getStripeCustomerId() == null) {
            throw new NoPaymentMethodException();
        }

        Shift shift = Shift.builder()
                .orgId(orgId)
                .title(request.title())
                .requiredSkills(request.requiredSkills() != null ? request.requiredSkills() : List.of())
                .hourlyRate(request.hourlyRate())
                .headcount(request.headcount())
                .filledCount(0)
                .siteLat(request.siteCenter().lat())
                .siteLon(request.siteCenter().lon())
                .geofenceRadiusM(request.geofenceRadiusM())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(ShiftStatus.DRAFT)
                .build();

        shift = shiftRepository.save(shift);

        BigDecimal estimatedBillRate = estimateBillRate(shift, vendor.getMarkupPctDefault());
        return ShiftResponse.from(shift, estimatedBillRate);
    }

    @Transactional
    public ShiftResponse publish(UUID orgId, UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId));

        if (!shift.getOrgId().equals(orgId)) {
            throw new ResourceNotFoundException("Shift", shiftId);
        }
        if (shift.getStatus() != ShiftStatus.DRAFT) {
            throw new com.vendorpulse.platform.common.exception.DomainExceptions.InvalidStateTransitionException(
                    "Only DRAFT shifts can be published, current status=" + shift.getStatus());
        }

        shift.setStatus(ShiftStatus.PUBLISHED);
        shift = shiftRepository.save(shift);

        Vendor vendor = vendorRepository.findById(orgId).orElseThrow();
        return ShiftResponse.from(shift, estimateBillRate(shift, vendor.getMarkupPctDefault()));
    }

    public List<ShiftResponse> feed() {
        return shiftRepository.findOpenFeed(Instant.now()).stream()
                .map(shift -> {
                    BigDecimal markup = vendorRepository.findById(shift.getOrgId())
                            .map(Vendor::getMarkupPctDefault)
                            .orElse(new BigDecimal("0.22"));
                    return ShiftResponse.from(shift, estimateBillRate(shift, markup));
                })
                .toList();
    }

    /** Rough at-a-glance estimate shown before any worker is matched — treats the full duration as regular hours. */
    private BigDecimal estimateBillRate(Shift shift, BigDecimal markupPct) {
        BigDecimal hours = BigDecimal.valueOf(
                Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_EVEN);

        ShiftPricingContext ctx = new ShiftPricingContext(
                shift.getOrgId(), shift.getId(), shift.getHourlyRate(),
                hours, BigDecimal.ZERO, BigDecimal.ZERO, markupPct);

        BigDecimal surge = surgeCalculator.multiplierFor(shift);
        return pricingEngineService.calculate(ctx, surge).billRate();
    }
}
