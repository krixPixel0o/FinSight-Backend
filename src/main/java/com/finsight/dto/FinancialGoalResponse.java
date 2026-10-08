package com.finsight.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.finsight.entity.FinancialGoal;

public record FinancialGoalResponse(Long id, String name, BigDecimal targetAmount, BigDecimal currentAmount,
        BigDecimal remainingAmount, BigDecimal progressPercent, BigDecimal plannedMonthlyContribution, String currency, LocalDate targetDate,
        boolean active, String scheduleStatus) {
    public static FinancialGoalResponse from(FinancialGoal goal) {
        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount()).max(BigDecimal.ZERO);
        BigDecimal progress = goal.getCurrentAmount().multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        String status = !goal.isActive() ? "INACTIVE"
                : remaining.signum() == 0 ? "COMPLETED"
                : goal.getTargetDate() == null ? "NO_TARGET_DATE"
                : goal.getTargetDate().isBefore(LocalDate.now()) ? "OVERDUE" : "IN_PROGRESS";
        return new FinancialGoalResponse(goal.getId(), goal.getName(), goal.getTargetAmount(),
                goal.getCurrentAmount(), remaining, progress, goal.getPlannedMonthlyContribution(), goal.getCurrency(), goal.getTargetDate(),
                goal.isActive(), status);
    }
}
