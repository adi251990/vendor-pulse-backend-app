package com.hireme.platform.timesheet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timesheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timesheet {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "clock_in_log_id")
    private Long clockInLogId;

    @Column(name = "clock_out_log_id")
    private Long clockOutLogId;

    @Column(name = "regular_hours", nullable = false)
    private BigDecimal regularHours;

    @Column(name = "overtime_hours", nullable = false)
    private BigDecimal overtimeHours;

    @Column(name = "holiday_hours", nullable = false)
    private BigDecimal holidayHours;

    @Column(name = "base_worker_pay")
    private BigDecimal baseWorkerPay;

    @Column(name = "platform_markup_fee")
    private BigDecimal platformMarkupFee;

    @Column(name = "vendor_bill_rate")
    private BigDecimal vendorBillRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status;

    @Column(name = "dispute_reason")
    private String disputeReason;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
