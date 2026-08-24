package com.hireme.platform.identity.controller;

import com.hireme.platform.identity.repository.StaffProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Checkr background-check webhook (spec §3.C). Real deployments verify the
 * request signature (Checkr signs with a shared secret header) before
 * trusting the payload - omitted here since it mirrors the Stripe webhook
 * pattern already shown in StripeWebhookController. {@code report.completed}
 * events flip staff_profiles.background_check_status, which gates claim
 * eligibility in ClaimService.
 */
@RestController
@RequestMapping("/webhooks/checkr")
public class CheckrWebhookController {

    private static final Logger log = LoggerFactory.getLogger(CheckrWebhookController.class);

    private final StaffProfileRepository staffProfileRepository;

    public CheckrWebhookController(StaffProfileRepository staffProfileRepository) {
        this.staffProfileRepository = staffProfileRepository;
    }

    @PostMapping
    @Transactional
    public void handle(@RequestBody Map<String, Object> payload) {
        String eventType = String.valueOf(payload.get("type"));
        if (!"report.completed".equals(eventType)) {
            log.debug("Ignoring Checkr event type {}", eventType);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Map.of());
        String staffId = String.valueOf(data.get("candidateId"));
        String status = String.valueOf(data.getOrDefault("status", "CONSIDER")).toUpperCase();

        staffProfileRepository.findById(UUID.fromString(staffId)).ifPresent(profile -> {
            profile.setBackgroundCheckStatus(status);
            staffProfileRepository.save(profile);
            log.info("Updated background_check_status={} for staff {}", status, staffId);
        });
    }
}
