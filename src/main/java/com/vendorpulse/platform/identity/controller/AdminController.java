package com.vendorpulse.platform.identity.controller;

import com.vendorpulse.platform.common.exception.DomainExceptions.ResourceNotFoundException;
import com.vendorpulse.platform.identity.entity.Vendor;
import com.vendorpulse.platform.identity.repository.VendorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Admin-only platform configuration — spec §1 (Admin: "configure platform markup fees"). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final VendorRepository vendorRepository;

    public AdminController(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @PatchMapping("/vendors/{orgId}/markup-config")
    @Transactional
    public Vendor updateMarkup(@PathVariable UUID orgId, @RequestBody MarkupUpdateRequest request) {
        Vendor vendor = vendorRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", orgId));
        vendor.setMarkupPctDefault(request.markupPctDefault());
        return vendorRepository.save(vendor);
    }

    public record MarkupUpdateRequest(BigDecimal markupPctDefault) {
    }
}
