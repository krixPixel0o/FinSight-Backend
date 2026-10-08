package com.finsight.dto;

import com.finsight.entity.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record AccountRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull AccountType type,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) java.math.BigDecimal openingBalance,
        boolean active) {
}
