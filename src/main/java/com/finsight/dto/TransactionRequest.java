package com.finsight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.finsight.entity.TransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

public record TransactionRequest(
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 160) String description,
        @NotNull LocalDate transactionDate,
        @Size(max = 500) String notes,
        Long accountId,
        Long sourceAccountId,
        Long targetAccountId,
        @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}
