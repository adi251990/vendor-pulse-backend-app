package com.vendorpulse.platform.payment.repository;

import com.vendorpulse.platform.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByTimesheetId(UUID timesheetId);

    Optional<Invoice> findByStripePaymentIntentId(String stripePaymentIntentId);
}
