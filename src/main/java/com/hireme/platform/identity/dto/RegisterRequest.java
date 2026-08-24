package com.hireme.platform.identity.dto;

import com.hireme.platform.identity.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
        @NotNull UserRole role,
        /** Required and validated by the service when role == OWNER; ignored otherwise. */
        String orgLegalName
) {
}
