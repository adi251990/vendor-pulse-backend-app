package com.vendorpulse.platform.payment.controller;

import com.vendorpulse.platform.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook receiver (spec §3.C: payment_intent.succeeded/failed,
 * account.updated). Signature-verified and idempotent by Stripe event id;
 * this scaffold logs and acknowledges - a production build persists
 * processed event ids to guard against Stripe's at-least-once redelivery
 * and dispatches into the same reconciliation path as the synchronous
 * charge call in PaymentService.
 */
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeProperties stripeProperties;

    public StripeWebhookController(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostMapping
    public ResponseEntity<String> handle(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String signatureHeader) {
        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.getWebhookSecret());
            log.info("Received verified Stripe event: {} ({})", event.getType(), event.getId());
            // Dispatch by event.getType(): payment_intent.succeeded/failed, account.updated, etc.
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }
    }
}
