package com.vendorpulse.platform.scheduler;

import com.vendorpulse.platform.timesheet.service.TimesheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Auto-approves timesheets that have sat PENDING_APPROVAL past the 48h owner-review SLA (spec §2.A / §3.C). */
@Component
public class TimesheetAutoApproveJob {

    private static final Logger log = LoggerFactory.getLogger(TimesheetAutoApproveJob.class);
    private static final long SLA_HOURS = 48;

    private final TimesheetService timesheetService;

    public TimesheetAutoApproveJob(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    @Scheduled(cron = "0 0 * * * *") // hourly
    public void run() {
        int count = timesheetService.autoApproveOverdue(Instant.now().minusSeconds(SLA_HOURS * 3600));
        if (count > 0) {
            log.info("Auto-approved {} timesheet(s) past the 48h review SLA", count);
        }
    }
}
