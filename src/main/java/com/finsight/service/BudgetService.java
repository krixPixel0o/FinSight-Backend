package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.BudgetRequest;
import com.finsight.dto.BudgetResponse;
import com.finsight.entity.Budget;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.BudgetRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.entity.TransactionType;
import com.finsight.exception.ConflictException;

@Service
public class BudgetService {
    private final BudgetRepository budgetRepository; private final TransactionRepository transactionRepository; private final UserRepository userRepository; private final CategoryRepository categoryRepository;
    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository, UserRepository userRepository, CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository; this.transactionRepository = transactionRepository; this.userRepository = userRepository; this.categoryRepository = categoryRepository;
    }
    @Transactional(readOnly = true)
    public List<BudgetResponse> findAll(String email) {
        User user = findUser(email); return budgetRepository.findByUserIdOrderByEndDateAsc(user.getId()).stream().map(b -> response(user, b)).toList();
    }
    @Transactional
    public BudgetResponse create(String email, BudgetRequest request) { return save(findUser(email), new Budget(), request); }
    @Transactional
    public BudgetResponse update(String email, Long id, BudgetRequest request) {
        User user = findUser(email); Budget budget = owned(user, id); return save(user, budget, request);
    }
    @Transactional
    public void delete(String email, Long id) { budgetRepository.delete(owned(findUser(email), id)); }
    private BudgetResponse save(User user, Budget budget, BudgetRequest request) {
        validate(request);
        if (request.endDate().isBefore(request.startDate())) throw new IllegalArgumentException("Budget end date must not be before start date");
        String categoryName = request.category().trim();
        if (!categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(user.getId(), categoryName, TransactionType.EXPENSE)) {
            throw new IllegalArgumentException("Choose one of your expense categories");
        }
        boolean overlap = budgetRepository.findByUserIdOrderByEndDateAsc(user.getId()).stream()
                .filter(existing -> !existing.getId().equals(budget.getId()))
                .anyMatch(existing -> existing.getCategory().equalsIgnoreCase(categoryName)
                        && !request.endDate().isBefore(existing.getStartDate())
                        && !request.startDate().isAfter(existing.getEndDate()));
        if (overlap) throw new ConflictException("A budget for this category already covers part of the selected dates");
        budget.setUser(user); budget.setCategory(categoryName); budget.setAmount(request.amount()); budget.setPeriod(request.period());
        budget.setStartDate(request.startDate()); budget.setEndDate(request.endDate()); return response(user, budgetRepository.save(budget));
    }
    private void validate(BudgetRequest request) {
        if (request == null) throw new IllegalArgumentException("Budget details are required");
        if (request.category() == null || request.category().isBlank()) throw new IllegalArgumentException("Budget category is required");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero");
        }
        if (request.period() == null) throw new IllegalArgumentException("Budget period is required");
        if (request.startDate() == null || request.endDate() == null) throw new IllegalArgumentException("Budget dates are required");
    }
    private BudgetResponse response(User user, Budget budget) {
        LocalDate today = LocalDate.now();
        LocalDate throughDate = budget.getEndDate().isBefore(today) ? budget.getEndDate() : today;
        BigDecimal spent = budget.getStartDate().isAfter(today)
                ? BigDecimal.ZERO
                : transactionRepository.sumExpenses(user.getId(), budget.getCategory(), budget.getStartDate(), throughDate);
        return BudgetResponse.from(budget, spent == null ? BigDecimal.ZERO : spent);
    }
    private Budget owned(User user, Long id) { return budgetRepository.findById(id).filter(b -> b.getUser().getId().equals(user.getId())).orElseThrow(() -> new ResourceNotFoundException("Budget not found")); }
    private User findUser(String email) { return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
}
