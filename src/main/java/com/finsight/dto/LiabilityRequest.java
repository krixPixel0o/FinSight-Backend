package com.finsight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.finsight.entity.LiabilityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LiabilityRequest(@NotBlank @Size(max = 100) String name, @NotNull LiabilityType type,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal currentBalance,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.0000") @Digits(integer = 3, fraction = 4) BigDecimal annualInterestRate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyPayment,
        @NotNull LocalDate asOfDate, boolean active) { }
