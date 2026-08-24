package com.hireme.platform.timesheet.dto;

import com.hireme.platform.timesheet.entity.Timesheet;

import java.math.BigDecimal;
import java.util.UUID;

public record TimesheetResponse(
        UUID id,
        UUID bookingId,
        String status,
        BigDecimal regularHours,
        BigDecimal overtimeHours,
        BigDecimal holidayHours,
        BigDecimal baseWorkerPay,
        BigDecimal platformMarkupFee,
        BigDecimal vendorBillRate
) {
    public static TimesheetResponse from(Timesheet t) {
        return new TimesheetResponse(t.getId(), t.getBookingId(), t.getStatus().name(),
                t.getRegularHours(), t.getOvertimeHours(), t.getHolidayHours(),
                t.getBaseWorkerPay(), t.getPlatformMarkupFee(), t.getVendorBillRate());
    }
}
