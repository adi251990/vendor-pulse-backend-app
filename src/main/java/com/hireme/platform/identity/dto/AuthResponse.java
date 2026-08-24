package com.hireme.platform.identity.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID userId,
        String role
) {
}
