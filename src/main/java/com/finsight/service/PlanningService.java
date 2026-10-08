package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.FinancialGoalRequest;
import com.finsight.dto.FinancialGoalResponse;
import com.finsight.dto.LiabilityRequest;
import com.finsight.dto.LiabilityResponse;
import com.finsight.entity.FinancialGoal;
import com.finsight.entity.Liability;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.FinancialGoalRepository;
import com.finsight.repository.LiabilityRepository;
import com.finsight.repository.UserRepository;

@Service
public class PlanningService {
    private static final int MAX_RECORDS_PER_USER = 100;
    private final FinancialGoalRepository goals;
    private final LiabilityRepository liabilities;
    private final UserRepository users;

    public PlanningService(FinancialGoalRepository goals, LiabilityRepository liabilities, UserRepository users) {
        this.goals = goals;
        this.liabilities = liabilities;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<FinancialGoalResponse> goals(String email) {
        User user = user(email);
        return goals.findByUserIdOrderByTargetDateAsc(user.getId()).stream().map(FinancialGoalResponse::from).toList();
    }

    @Transactional
    public FinancialGoalResponse createGoal(String email, FinancialGoalRequest request) {
        User user = user(email);
        if (goals.countByUserId(user.getId()) >= MAX_RECORDS_PER_USER) throw new IllegalArgumentException("A maximum of 100 goals is allowed");
        return saveGoal(user, new FinancialGoal(), request);
    }

    @Transactional
    public FinancialGoalResponse updateGoal(String email, Long id, FinancialGoalRequest request) {
        User user = user(email);
        FinancialGoal goal = goals.findById(id).filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        return saveGoal(user, goal, request);
    }

    @Transactional
    public void deleteGoal(String email, Long id) {
        User user = user(email);
        goals.delete(goals.findById(id).filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found")));
    }

    @Transactional(readOnly = true)
    public List<LiabilityResponse> liabilities(String email) {
        User user = user(email);
        return liabilities.findByUserIdOrderByNameAsc(user.getId()).stream().map(LiabilityResponse::from).toList();
    }

    @Transactional
    public LiabilityResponse createLiability(String email, LiabilityRequest request) {
        User user = user(email);
        if (liabilities.countByUserId(user.getId()) >= MAX_RECORDS_PER_USER) throw new IllegalArgumentException("A maximum of 100 liabilities is allowed");
        return saveLiability(user, new Liability(), request);
    }

    @Transactional
    public LiabilityResponse updateLiability(String email, Long id, LiabilityRequest request) {
        User user = user(email);
        Liability liability = liabilities.findById(id).filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Liability not found"));
        return saveLiability(user, liability, request);
    }

    @Transactional
    public void deleteLiability(String email, Long id) {
        User user = user(email);
        liabilities.delete(liabilities.findById(id).filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Liability not found")));
    }

    private FinancialGoalResponse saveGoal(User user, FinancialGoal goal, FinancialGoalRequest request) {
        if (request == null || request.targetAmount() == null || request.currentAmount() == null
                || request.targetAmount().compareTo(BigDecimal.ZERO) <= 0 || request.currentAmount().signum() < 0
                || request.plannedMonthlyContribution() == null || request.plannedMonthlyContribution().signum() < 0
                || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Provide a goal name, positive target, and non-negative saved amount");
        }
        goal.setUser(user);
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setCurrentAmount(request.currentAmount());
        goal.setPlannedMonthlyContribution(request.plannedMonthlyContribution());
        goal.setTargetDate(request.targetDate());
        goal.setCurrency(user.getCurrency());
        goal.setActive(request.active());
        return FinancialGoalResponse.from(goals.save(goal));
    }

    private LiabilityResponse saveLiability(User user, Liability liability, LiabilityRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.type() == null
                || request.currentBalance() == null || request.currentBalance().signum() < 0
                || request.monthlyPayment() == null || request.monthlyPayment().signum() < 0
                || request.annualInterestRate() == null || request.annualInterestRate().signum() < 0
                || request.annualInterestRate().compareTo(BigDecimal.valueOf(100)) > 0
                || request.asOfDate() == null || request.asOfDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Provide valid liability details and a current or past as-of date");
        }
        liability.setUser(user);
        liability.setName(request.name().trim());
        liability.setType(request.type());
        liability.setCurrentBalance(request.currentBalance());
        liability.setAnnualInterestRate(request.annualInterestRate());
        liability.setMonthlyPayment(request.monthlyPayment());
        liability.setCurrency(user.getCurrency());
        liability.setAsOfDate(request.asOfDate());
        liability.setActive(request.active());
        return LiabilityResponse.from(liabilities.save(liability));
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
