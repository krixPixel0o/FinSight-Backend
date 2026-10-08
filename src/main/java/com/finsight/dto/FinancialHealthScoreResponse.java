package com.finsight.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record FinancialHealthScoreResponse(
        Integer score,
        String status,
        String rating,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        long transactionsAnalyzed,
        Map<String, Component> components,
        List<String> unavailableDimensions) {

    public record Component(int score, int weight, String explanation, String evidence) { }
}
