package com.hireme.platform.booking.controller;

import com.hireme.platform.booking.dto.ClaimResponse;
import com.hireme.platform.booking.service.ClaimService;
import com.hireme.platform.identity.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final ClaimService claimService;

    public BookingController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /**
     * POST /bookings/{shiftId}/claim — spec §3.C.
     * 200 on success, 409 SHIFT_ALREADY_CLAIMED if another worker won the
     * race (see ClaimService for the Redisson-lock + optimistic-lock design),
     * 410 GONE if the shift is no longer open, 403 if compliance gates fail.
     */
    @PostMapping("/{shiftId}/claim")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ClaimResponse> claim(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID shiftId) {
        return ResponseEntity.ok(claimService.claim(shiftId, principal.userId()));
    }
}
