package com.vendorpulse.platform.shift.entity;

/** Mirrors the shift_status Postgres enum and the state machine in spec §2.A. */
public enum ShiftStatus {
    DRAFT,
    PUBLISHED,
    MATCHING,
    FILLED,
    IN_PROGRESS,
    COMPLETED,
    DISPUTED,
    UNDER_REVIEW,
    INVOICED,
    PAID,
    NO_SHOW,
    UNFILLED,
    CANCELLED
}
