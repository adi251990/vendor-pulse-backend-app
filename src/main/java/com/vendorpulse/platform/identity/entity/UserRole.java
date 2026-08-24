package com.vendorpulse.platform.identity.entity;

/** Maps to Postgres enum user_role and to Spring Security authorities ROLE_&lt;name&gt;. */
public enum UserRole {
    STAFF,
    OWNER,
    ADMIN
}
