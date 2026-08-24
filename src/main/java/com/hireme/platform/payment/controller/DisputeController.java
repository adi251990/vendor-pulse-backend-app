package com.hireme.platform.payment.controller;

import com.hireme.platform.identity.security.UserPrincipal;
import com.hireme.platform.payment.entity.Dispute;
import com.hireme.platform.payment.service.DisputeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Admin dispute-resolution panel backend — spec §1 (Admin role) and §4.2. */
@RestController
@RequestMapping("/api/v1/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Dispute> assign(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(disputeService.assign(id, principal.userId()));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Dispute> resolve(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID id,
                                            @RequestBody ResolveRequest request) {
        Dispute resolved = disputeService.resolve(id, principal.userId(), request.resolutionNote(),
                request.adjustedRegularHours(), request.adjustedOvertimeHours(), request.adjustedHolidayHours());
        return ResponseEntity.ok(resolved);
    }

    /** If adjustedRegularHours is null, the original timesheet hours are confirmed as-is. */
    public record ResolveRequest(String resolutionNote, BigDecimal adjustedRegularHours,
                                  BigDecimal adjustedOvertimeHours, BigDecimal adjustedHolidayHours) {
    }
}
