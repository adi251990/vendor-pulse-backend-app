package com.hireme.platform.identity.security;

import com.hireme.platform.identity.entity.UserRole;

import java.util.UUID;

/**
 * Authenticated-request principal decoded straight from JWT claims — no DB
 * hit needed per request. {@code orgId} is null for STAFF/ADMIN tokens.
 */
public record UserPrincipal(UUID userId, UUID orgId, UserRole role) {

    public boolean hasRole(UserRole expected) {
        return this.role == expected;
    }
}
