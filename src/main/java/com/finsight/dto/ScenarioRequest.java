package com.finsight.dto;

import java.math.BigDecimal;

import com.finsight.entity.ScenarioType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ScenarioRequest(
        ScenarioType type,
        @Size(max = 80) String category,
        @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monthlyAmount,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal percentage,
        @Min(1) @Max(24) Integer horizonMonths) { }
