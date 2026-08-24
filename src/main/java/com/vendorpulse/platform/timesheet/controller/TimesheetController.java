package com.vendorpulse.platform.timesheet.controller;

import com.vendorpulse.platform.identity.security.UserPrincipal;
import com.vendorpulse.platform.timesheet.dto.DisputeRequest;
import com.vendorpulse.platform.timesheet.dto.TimesheetResponse;
import com.vendorpulse.platform.timesheet.service.TimesheetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timesheets")
public class TimesheetController {

    private final TimesheetService timesheetService;

    public TimesheetController(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TimesheetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(TimesheetResponse.from(timesheetService.get(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<TimesheetResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(TimesheetResponse.from(timesheetService.approve(id)));
    }

    @PostMapping("/{id}/dispute")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<TimesheetResponse> dispute(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody DisputeRequest request) {
        return ResponseEntity.ok(TimesheetResponse.from(
                timesheetService.dispute(id, principal.userId(), request.reason())));
    }
}
