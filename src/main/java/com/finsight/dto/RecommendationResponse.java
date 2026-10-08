package com.finsight.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendationResponse(LocalDate periodStart, LocalDate periodEnd,
                                    List<Recommendation> recommendations) {
    public record Recommendation(String type, String priority, String title, String explanation,
                                 String evidence, String suggestedAction) { }
}
