package com.vendorpulse.platform.notification.service;

import com.vendorpulse.platform.booking.entity.Booking;
import com.vendorpulse.platform.config.TwilioProperties;
import com.vendorpulse.platform.identity.entity.User;
import com.vendorpulse.platform.identity.repository.UserRepository;
import com.vendorpulse.platform.shift.entity.Shift;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Twilio SMS + push-notification dispatch. Push delivery (FCM to the
 * Android client) is stubbed as a log line here — wiring in Firebase Cloud
 * Messaging is a drop-in replacement for {@link #sendPush} once the Android
 * app registers device tokens against the user record.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TwilioProperties twilioProperties;
    private final UserRepository userRepository;
    private boolean twilioReady = false;

    public NotificationService(TwilioProperties twilioProperties, UserRepository userRepository) {
        this.twilioProperties = twilioProperties;
        this.userRepository = userRepository;
    }

    @PostConstruct
    void initTwilio() {
        try {
            Twilio.init(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());
            twilioReady = true;
        } catch (Exception e) {
            log.warn("Twilio not configured (placeholder credentials?) - SMS sends will be logged only: {}", e.getMessage());
        }
    }

    public void sendPush(java.util.UUID userId, String title, String body) {
        // TODO: replace with FCM HttpV1Api send once device tokens are captured from the Android app.
        log.info("[PUSH] to user {} :: {} - {}", userId, title, body);
    }

    public void sendSms(String toPhoneNumber, String body) {
        if (!twilioReady) {
            log.info("[SMS-SIMULATED] to {} :: {}", toPhoneNumber, body);
            return;
        }
        try {
            Message.creator(new PhoneNumber(toPhoneNumber), new PhoneNumber(twilioProperties.getFromNumber()), body).create();
        } catch (Exception e) {
            log.warn("Twilio SMS send failed to {}: {}", toPhoneNumber, e.getMessage());
        }
    }

    public void notifyOwnerOfNoShow(Shift shift, Booking booking) {
        userRepository.findByOrgId(shift.getOrgId())
                .forEach(owner -> {
                    sendPush(owner.getId(), "Worker no-show",
                            "A worker no-showed for \"" + shift.getTitle() + "\" — backup dispatch in progress.");
                    sendSms(owner.getPhone(), "VendorPulse: no-show detected on \"" + shift.getTitle()
                            + "\", we're dispatching a backup worker now.");
                });
    }

    public void notifyStaffOfBackupDispatch(Shift shift, Booking backupBooking) {
        userRepository.findById(backupBooking.getStaffId()).ifPresent((User staff) -> {
            sendPush(staff.getId(), "Urgent shift available",
                    "\"" + shift.getTitle() + "\" needs a worker now — surge pay applies.");
            sendSms(staff.getPhone(), "VendorPulse URGENT: \"" + shift.getTitle()
                    + "\" needs you now, surge pay applies. Open the app to confirm.");
        });
    }

    /** Notification cascade escalation levels — spec §2.B. */
    public void escalate(Shift shift, CascadeLevel level, Iterable<String> candidatePhoneNumbers) {
        for (String phone : candidatePhoneNumbers) {
            switch (level) {
                case T_MINUS_2H -> sendPush(null, "Shift starting soon", cascadeMessage(shift, level));
                case T_MINUS_90MIN, T_MINUS_60MIN, T_MINUS_30MIN -> sendSms(phone, cascadeMessage(shift, level));
            }
        }
    }

    private String cascadeMessage(Shift shift, CascadeLevel level) {
        return switch (level) {
            case T_MINUS_2H -> "\"" + shift.getTitle() + "\" is still open, starting in ~2h.";
            case T_MINUS_90MIN -> "\"" + shift.getTitle() + "\" still needs workers — starting in 90 min.";
            case T_MINUS_60MIN -> "Surge pay now active for \"" + shift.getTitle() + "\" — starts in 1h.";
            case T_MINUS_30MIN -> "URGENT — surge pay for \"" + shift.getTitle() + "\", starts in 30 min!";
        };
    }

    public enum CascadeLevel {
        T_MINUS_2H, T_MINUS_90MIN, T_MINUS_60MIN, T_MINUS_30MIN
    }
}
