package com.finsight.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finsight.dto.FinancialHealthScoreResponse;
import com.finsight.dto.AnomalyResponse;
import com.finsight.dto.ForecastResponse;
import com.finsight.dto.ScenarioRequest;
import com.finsight.dto.ScenarioResponse;
import com.finsight.dto.RecommendationResponse;
import com.finsight.dto.AssistantRequest;
import com.finsight.dto.AssistantResponse;
import com.finsight.service.AnomalyDetectionService;
import com.finsight.service.FinancialProjectionService;
import com.finsight.service.FinancialHealthScoreService;
import com.finsight.service.FinancialRecommendationService;
import com.finsight.service.FinancialAssistantService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/API/intelligence")
public class FinancialIntelligenceController {

    private final FinancialHealthScoreService healthScoreService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final FinancialProjectionService projectionService;
    private final FinancialRecommendationService recommendationService;
    private final FinancialAssistantService assistantService;

    public FinancialIntelligenceController(FinancialHealthScoreService healthScoreService,
                                           AnomalyDetectionService anomalyDetectionService,
                                           FinancialProjectionService projectionService,
                                           FinancialRecommendationService recommendationService,
                                           FinancialAssistantService assistantService) {
        this.healthScoreService = healthScoreService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.projectionService = projectionService;
        this.recommendationService = recommendationService;
        this.assistantService = assistantService;
    }

    @GetMapping("/health-score")
    public FinancialHealthScoreResponse healthScore(Authentication authentication) {
        return healthScoreService.calculate(authentication.getName());
    }

    @GetMapping("/anomalies")
    public AnomalyResponse anomalies(Authentication authentication) {
        return anomalyDetectionService.detect(authentication.getName());
    }

    @GetMapping("/recommendations")
    public RecommendationResponse recommendations(Authentication authentication) {
        return recommendationService.recommend(authentication.getName());
    }

    @PostMapping("/assistant")
    public AssistantResponse assistant(Authentication authentication, @Valid @RequestBody AssistantRequest request) {
        return assistantService.ask(authentication.getName(), request.message());
    }

    @GetMapping("/forecast")
    public ForecastResponse forecast(Authentication authentication,
                                     @RequestParam(defaultValue = "6") int months) {
        return projectionService.forecast(authentication.getName(), months);
    }

    @PostMapping("/scenario")
    public ScenarioResponse scenario(Authentication authentication, @Valid @RequestBody ScenarioRequest request) {
        return projectionService.scenario(authentication.getName(), request);
    }
}
