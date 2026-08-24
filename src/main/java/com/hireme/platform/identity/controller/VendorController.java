package com.hireme.platform.identity.controller;

import com.hireme.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.hireme.platform.identity.entity.Vendor;
import com.hireme.platform.identity.repository.VendorRepository;
import com.hireme.platform.identity.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Owner-side org settings. {@code stripeCustomerId} is populated once the
 * Owner completes Stripe's hosted Setup Intent flow in the Android/web
 * onboarding UI (card-on-file capture happens client-side against Stripe
 * directly; only the resulting customer id round-trips to this endpoint) -
 * see the 402 NO_PAYMENT_METHOD gate in ShiftService.createDraft.
 */
@RestController
@RequestMapping("/api/v1/vendors/me")
@PreAuthorize("hasRole('OWNER')")
public class VendorController {

    private final VendorRepository vendorRepository;

    public VendorController(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @GetMapping
    public Vendor get(@AuthenticationPrincipal UserPrincipal principal) {
        return vendorRepository.findById(principal.orgId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", principal.orgId()));
    }

    @PatchMapping("/payment-method")
    @Transactional
    public Vendor setPaymentMethod(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody SetPaymentMethodRequest request) {
        Vendor vendor = vendorRepository.findById(principal.orgId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", principal.orgId()));
        vendor.setStripeCustomerId(request.stripeCustomerId());
        return vendorRepository.save(vendor);
    }

    public record SetPaymentMethodRequest(String stripeCustomerId) {
    }
}
