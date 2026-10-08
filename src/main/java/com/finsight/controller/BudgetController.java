package com.finsight.controller;

import com.finsight.dto.BudgetRequest;
import com.finsight.dto.BudgetResponse;
import com.finsight.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/API/budgets")
public class BudgetController {
    private final BudgetService budgetService;
    public BudgetController(BudgetService budgetService) { this.budgetService = budgetService; }
    @GetMapping public List<BudgetResponse> findAll(Authentication authentication) { return budgetService.findAll(authentication.getName()); }
    @PostMapping public ResponseEntity<BudgetResponse> create(Authentication authentication, @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(authentication.getName(), request));
    }
    @PutMapping("/{id}") public BudgetResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return budgetService.update(authentication.getName(), id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        budgetService.delete(authentication.getName(), id); return ResponseEntity.noContent().build();
    }
}