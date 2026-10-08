package com.finsight.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.finsight.dto.FinancialGoalRequest;
import com.finsight.dto.FinancialGoalResponse;
import com.finsight.dto.LiabilityRequest;
import com.finsight.dto.LiabilityResponse;
import com.finsight.service.PlanningService;

@RestController
@RequestMapping("/API")
public class PlanningController {
    private final PlanningService service;
    public PlanningController(PlanningService service) { this.service = service; }

    @GetMapping("/goals") public List<FinancialGoalResponse> goals(Authentication auth) { return service.goals(auth.getName()); }
    @PostMapping("/goals") public ResponseEntity<FinancialGoalResponse> createGoal(Authentication auth, @Valid @RequestBody FinancialGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGoal(auth.getName(), request));
    }
    @PutMapping("/goals/{id}") public FinancialGoalResponse updateGoal(Authentication auth, @PathVariable Long id, @Valid @RequestBody FinancialGoalRequest request) {
        return service.updateGoal(auth.getName(), id, request);
    }
    @DeleteMapping("/goals/{id}") public ResponseEntity<Void> deleteGoal(Authentication auth, @PathVariable Long id) {
        service.deleteGoal(auth.getName(), id); return ResponseEntity.noContent().build();
    }

    @GetMapping("/liabilities") public List<LiabilityResponse> liabilities(Authentication auth) { return service.liabilities(auth.getName()); }
    @PostMapping("/liabilities") public ResponseEntity<LiabilityResponse> createLiability(Authentication auth, @Valid @RequestBody LiabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLiability(auth.getName(), request));
    }
    @PutMapping("/liabilities/{id}") public LiabilityResponse updateLiability(Authentication auth, @PathVariable Long id, @Valid @RequestBody LiabilityRequest request) {
        return service.updateLiability(auth.getName(), id, request);
    }
    @DeleteMapping("/liabilities/{id}") public ResponseEntity<Void> deleteLiability(Authentication auth, @PathVariable Long id) {
        service.deleteLiability(auth.getName(), id); return ResponseEntity.noContent().build();
    }
}
