package com.vendorpulse.platform.shift.controller;

import com.vendorpulse.platform.identity.security.UserPrincipal;
import com.vendorpulse.platform.shift.dto.CreateShiftRequest;
import com.vendorpulse.platform.shift.dto.ShiftResponse;
import com.vendorpulse.platform.shift.service.ShiftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    /** POST /shifts — spec §3.C. Requires the Owner's org to have a Stripe payment method on file (402 otherwise). */
    @PostMapping("/shifts")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ShiftResponse> createShift(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody CreateShiftRequest request) {
        ShiftResponse response = shiftService.createDraft(principal.orgId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/shifts/{shiftId}/publish")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ShiftResponse> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable UUID shiftId) {
        return ResponseEntity.ok(shiftService.publish(principal.orgId(), shiftId));
    }

    /** GET /shifts/feed — geo/skill-ranked open shifts for the Staff Android home feed. */
    @GetMapping("/shifts/feed")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<List<ShiftResponse>> feed() {
        return ResponseEntity.ok(shiftService.feed());
    }
}
