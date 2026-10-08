package com.finsight.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        String currency,
        BigDecimal currentBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpenses,
        BigDecimal netCashFlow,
        BigDecimal savingsRate,
        Map<String, BigDecimal> categorySpending,
        Map<String, BigDecimal> monthlySpending,
        List<TransactionResponse> recentTransactions,
        List<String> insights) {
}
