package com.vendorpulse.platform.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "timesheet_id", nullable = false)
    private UUID timesheetId;

    @Column(name = "raised_by", nullable = false)
    private UUID raisedBy;

    @Column(nullable = false)
    private String reason;

    @Column(name = "escrow_invoice_id")
    private UUID escrowInvoiceId;

    @Column(name = "assigned_admin_id")
    private UUID assignedAdminId;

    private String resolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
