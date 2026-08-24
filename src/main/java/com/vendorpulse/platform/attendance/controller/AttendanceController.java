package com.vendorpulse.platform.attendance.controller;

import com.vendorpulse.platform.attendance.dto.ClockEventRequest;
import com.vendorpulse.platform.attendance.dto.ClockEventResponse;
import com.vendorpulse.platform.attendance.service.ClockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timesheets")
public class AttendanceController {

    private final ClockService clockService;

    public AttendanceController(ClockService clockService) {
        this.clockService = clockService;
    }

    /** POST /timesheets/clock-in — spec §3.C. 201 on accept, 202 if flagged for late-sync review, 422 OUTSIDE_GEOFENCE. */
    @PostMapping("/clock-in")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ClockEventResponse> clockIn(@Valid @RequestBody ClockEventRequest request) {
        ClockEventResponse response = clockService.clockIn(request);
        HttpStatus status = "ACCEPTED".equals(response.syncStatus()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/clock-out")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ClockEventResponse> clockOut(@Valid @RequestBody ClockEventRequest request) {
        ClockEventResponse response = clockService.clockOut(request);
        HttpStatus status = "ACCEPTED".equals(response.syncStatus()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }
}
