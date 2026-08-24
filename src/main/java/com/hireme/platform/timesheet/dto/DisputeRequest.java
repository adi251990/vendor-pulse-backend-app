package com.hireme.platform.timesheet.dto;

import jakarta.validation.constraints.NotBlank;

public record DisputeRequest(@NotBlank String reason) {
}
