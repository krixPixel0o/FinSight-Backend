package com.finsight.dto;

import com.finsight.entity.BudgetPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetRequest(@NotBlank @jakarta.validation.constraints.Size(max = 80) String category,
                            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                            @NotNull BudgetPeriod period, @NotNull LocalDate startDate, @NotNull LocalDate endDate) { }
