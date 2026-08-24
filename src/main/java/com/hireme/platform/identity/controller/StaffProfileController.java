package com.hireme.platform.identity.controller;

import com.hireme.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.hireme.platform.identity.entity.StaffProfile;
import com.hireme.platform.identity.repository.StaffProfileRepository;
import com.hireme.platform.identity.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff self-service profile — availability location, skills, payout account (spec §1). */
@RestController
@RequestMapping("/api/v1/staff/me")
@PreAuthorize("hasRole('STAFF')")
public class StaffProfileController {

    private final StaffProfileRepository staffProfileRepository;

    public StaffProfileController(StaffProfileRepository staffProfileRepository) {
        this.staffProfileRepository = staffProfileRepository;
    }

    @GetMapping
    public StaffProfile get(@AuthenticationPrincipal UserPrincipal principal) {
        return staffProfileRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", principal.userId()));
    }

    /** Sets the Stripe Connect Express account id once onboarding completes (spec §2.D). */
    @PatchMapping("/payout-account")
    @Transactional
    public StaffProfile setPayoutAccount(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody SetPayoutAccountRequest request) {
        StaffProfile profile = get(principal);
        profile.setStripeAccountId(request.stripeAccountId());
        return staffProfileRepository.save(profile);
    }

    /** Real-time availability location + skill tags used by the matching engine (spec §2.B). */
    @PatchMapping("/availability")
    @Transactional
    public StaffProfile setAvailability(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody SetAvailabilityRequest request) {
        StaffProfile profile = get(principal);
        profile.setHomeLat(request.lat());
        profile.setHomeLon(request.lon());
        if (request.skillTags() != null) {
            profile.setSkillTags(request.skillTags());
        }
        return staffProfileRepository.save(profile);
    }

    public record SetPayoutAccountRequest(String stripeAccountId) {
    }

    public record SetAvailabilityRequest(double lat, double lon, List<String> skillTags) {
    }
}
