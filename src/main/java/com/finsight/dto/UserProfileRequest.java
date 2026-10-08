package com.finsight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(
        @NotBlank @Size(min = 2, max = 80) String name,
        @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}
