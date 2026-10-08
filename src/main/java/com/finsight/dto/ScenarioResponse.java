package com.finsight.dto;

import java.math.BigDecimal;
import java.util.List;

public record ScenarioResponse(
        String status,
        String type,
        String currency,
        String category,
        int horizonMonths,
        BigDecimal baselineMonthlyIncome,
        BigDecimal baselineMonthlyExpenses,
        BigDecimal baselineMonthlySurplus,
        BigDecimal monthlyChange,
        BigDecimal scenarioMonthlySurplus,
        BigDecimal projectedCumulativeChange,
        List<String> assumptions) { }
