package com.finsight.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.finsight.entity.Budget;
import com.finsight.entity.BudgetPeriod;

public record BudgetResponse(Long id, String category, BigDecimal amount, BudgetPeriod period,
                             LocalDate startDate, LocalDate endDate, BigDecimal spent,
                             BigDecimal remaining, BigDecimal percentageUsed, String status) {
    public static BudgetResponse from(Budget budget, BigDecimal spent) {
        BigDecimal remaining = budget.getAmount().subtract(spent);
        BigDecimal percentage = budget.getAmount().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : spent.multiply(BigDecimal.valueOf(100))
                        .divide(budget.getAmount(), 2, RoundingMode.HALF_UP);
        String status = percentage.compareTo(BigDecimal.valueOf(100)) >= 0 ? "EXCEEDED"
                : percentage.compareTo(BigDecimal.valueOf(75)) >= 0 ? "WARNING" : "SAFE";
        return new BudgetResponse(budget.getId(), budget.getCategory(), budget.getAmount(), budget.getPeriod(),
                budget.getStartDate(), budget.getEndDate(), spent, remaining, percentage, status);
    }
}
