package com.vendorpulse.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Thin wrapper around the Kafka producer for the domain-event backbone
 * described in spec §3.A (shift.published, booking.claimed, timesheet.*,
 * payment.*, worker.noshow). Modules publish fire-and-forget; consumers
 * (NotificationService, PaymentService, reporting) subscribe independently
 * so any one of them being slow/down never blocks the request that
 * triggered the event.
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, key, payload);
        } catch (Exception e) {
            // Never let a broker hiccup fail the calling request/transaction - log and move on.
            // A production deployment should back this with an outbox table + relay so events
            // survive a broker outage instead of being silently dropped here.
            log.warn("Failed to publish event to topic {} (key={}): {}", topic, key, e.getMessage());
        }
    }
}
