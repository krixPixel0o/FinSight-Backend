package com.finsight.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Fixed, read-only allowlist of aggregate finance queries available to the assistant. */
@Service
public class FinancialAssistantContextService {
    private final FinancialHealthScoreService healthScore;
    private final FinancialProjectionService projections;
    private final FinancialRecommendationService recommendations;
    private final AnomalyDetectionService anomalies;

    public FinancialAssistantContextService(FinancialHealthScoreService healthScore,
            FinancialProjectionService projections, FinancialRecommendationService recommendations,
            AnomalyDetectionService anomalies) {
        this.healthScore = healthScore;
        this.projections = projections;
        this.recommendations = recommendations;
        this.anomalies = anomalies;
    }

    public Map<String, Object> readOnlyContext(String email) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("healthScore", healthScore.calculate(email));
        context.put("forecast", projections.forecast(email, 6));
        context.put("recommendations", recommendations.recommend(email));
        var anomalyReport = anomalies.detect(email);
        context.put("potentiallyUnusualExpenseSummary", Map.of(
                "status", anomalyReport.status(),
                "historyLimited", anomalyReport.historyLimited(),
                "signals", anomalyReport.anomalies().stream().map(item -> Map.of(
                        "category", item.category(), "amount", item.amount(),
                        "baselineAverage", item.baselineAverage(),
                        "baselineTransactions", item.baselineTransactions(),
                        "signalStrength", item.signalStrength())).toList()));
        return Map.copyOf(context);
    }
}
