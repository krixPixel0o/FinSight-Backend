package com.finsight.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ForecastResponse(
        String status,
        String currency,
        String method,
        String confidence,
        int monthsOfHistory,
        BigDecimal monthlyAverageIncome,
        BigDecimal monthlyAverageExpenses,
        BigDecimal monthlyAverageSurplus,
        BigDecimal netFlowStandardDeviation,
        BigDecimal currentBalance,
        List<Projection> projections,
        List<BudgetRunway> budgetRunways,
        List<GoalProjection> goalProjections,
        List<String> assumptions,
        List<String> unavailableProjections) {

    public record Projection(YearMonth month, BigDecimal projectedIncome,
                             BigDecimal projectedExpenses, BigDecimal projectedSurplus,
                             BigDecimal projectedEndingBalance) { }

    public record BudgetRunway(Long budgetId, String category, BigDecimal remaining,
                               BigDecimal recentMonthlyExpenseAverage, BigDecimal estimatedMonthsToLimit,
                               String status) { }

    public record GoalProjection(Long goalId, String name, BigDecimal remainingAmount,
                                 BigDecimal plannedMonthlyContribution, Integer estimatedMonths,
                                 YearMonth estimatedCompletionMonth, String status) { }
}
