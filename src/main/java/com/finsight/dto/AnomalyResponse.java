package com.finsight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnomalyResponse(
        String status,
        LocalDate recentPeriodStart,
        LocalDate periodEnd,
        LocalDate baselinePeriodStart,
        LocalDate baselinePeriodEnd,
        int minimumBaselineTransactions,
        boolean historyLimited,
        List<Anomaly> anomalies) {

    public record Anomaly(Long transactionId, LocalDate date, String category, String description,
                          BigDecimal amount, String currency, int baselineTransactions,
                          BigDecimal baselineAverage, BigDecimal standardDeviation,
                          BigDecimal deviationFromAverage, int signalStrength,
                          String explanation) { }
}
