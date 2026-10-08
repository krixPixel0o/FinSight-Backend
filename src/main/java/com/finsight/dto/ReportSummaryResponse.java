package com.finsight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ReportSummaryResponse(LocalDate from, LocalDate to, BigDecimal income, BigDecimal expenses,
                                   BigDecimal netCashFlow, BigDecimal savingsRate, long transactionCount,
                                   Map<String, BigDecimal> categoryExpenses,
                                   Map<String, BigDecimal> monthlyExpenses) { }
