package com.finsight.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.RecommendationResponse;
import com.finsight.dto.RecommendationResponse.Recommendation;
import com.finsight.entity.Budget;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.BudgetRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

/** Deterministic suggestions grounded in recent user-owned records. */
@Service
public class FinancialRecommendationService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final BudgetRepository budgets;

    public FinancialRecommendationService(UserRepository users, TransactionRepository transactions,
                                          BudgetRepository budgets) {
        this.users = users;
        this.transactions = transactions;
        this.budgets = budgets;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate end = LocalDate.now().withDayOfMonth(1).minusDays(1);
        LocalDate recentStart = YearMonth.from(end).minusMonths(2).atDay(1);
        LocalDate previousStart = YearMonth.from(recentStart).minusMonths(3).atDay(1);
        LocalDate previousEnd = recentStart.minusDays(1);
        List<Recommendation> result = new ArrayList<>();

        BigDecimal recentIncome = safe(transactions.sumByTypeBetween(user.getId(), TransactionType.INCOME, recentStart, end));
        BigDecimal recentExpense = safe(transactions.sumByTypeBetween(user.getId(), TransactionType.EXPENSE, recentStart, end));
        if (recentIncome.signum() > 0 && recentExpense.compareTo(recentIncome) > 0) {
            result.add(new Recommendation("CASH_FLOW", "HIGH", "Recorded expenses exceeded income",
                    "Across the latest three completed months, recorded expenses were higher than recorded income.",
                    "Income " + recentIncome.toPlainString() + "; expenses " + recentExpense.toPlainString()
                            + " in " + recentStart + " through " + end + ".",
                    "Review the largest recent expense categories and confirm that income and transactions are complete."));
        }

        List<Object[]> recentByCategory = transactions.sumByCategoryBetween(user.getId(), TransactionType.EXPENSE, recentStart, end);
        Map<String, BigDecimal> priorByCategory = new java.util.HashMap<>();
        for (Object[] row : transactions.sumByCategoryBetween(user.getId(), TransactionType.EXPENSE, previousStart, previousEnd)) {
            priorByCategory.put(String.valueOf(row[0]).toLowerCase(java.util.Locale.ROOT), (BigDecimal) row[1]);
        }
        recentByCategory.stream().sorted(Comparator.comparing(row -> (BigDecimal) row[1], Comparator.reverseOrder()))
                .limit(3).forEach(row -> {
                    String category = String.valueOf(row[0]);
                    BigDecimal current = (BigDecimal) row[1];
                    BigDecimal prior = priorByCategory.get(category.toLowerCase(java.util.Locale.ROOT));
                    if (prior != null && prior.signum() > 0
                            && current.compareTo(prior.multiply(new BigDecimal("1.20"))) > 0
                            && current.subtract(prior).compareTo(new BigDecimal("100")) >= 0) {
                        BigDecimal change = current.subtract(prior).multiply(HUNDRED).divide(prior, 1, RoundingMode.HALF_UP);
                        result.add(new Recommendation("CATEGORY_INCREASE", "MEDIUM", category + " spending increased",
                                "Recorded spending in this category rose materially compared with the prior three completed months.",
                                "" + category + ": " + prior.toPlainString() + " previously vs " + current.toPlainString()
                                        + " recently (" + change.toPlainString() + "% increase).",
                                "Check whether the increase is expected, recurring, or caused by missing or miscategorized entries."));
                    }
                });

        List<Budget> active = budgets.findByUserIdOrderByEndDateAsc(user.getId()).stream()
                .filter(b -> !b.getStartDate().isAfter(LocalDate.now()) && !b.getEndDate().isBefore(LocalDate.now())).toList();
        for (Budget budget : active) {
            BigDecimal spent = safe(transactions.sumExpenses(user.getId(), budget.getCategory(), budget.getStartDate(), LocalDate.now()));
            BigDecimal utilization = spent.multiply(HUNDRED).divide(budget.getAmount(), 1, RoundingMode.HALF_UP);
            if (utilization.compareTo(new BigDecimal("80")) >= 0) {
                result.add(new Recommendation("BUDGET_THRESHOLD", utilization.compareTo(HUNDRED) >= 0 ? "HIGH" : "MEDIUM",
                        budget.getCategory() + " budget is nearing its limit",
                        "Recorded spending has used at least 80% of the active budget.",
                        "Spent " + spent.toPlainString() + " of " + budget.getAmount().toPlainString() + " ("
                                + utilization.toPlainString() + "%), through " + LocalDate.now() + ".",
                        "Review remaining planned expenses for this budget period; the estimate excludes future-dated transactions."));
            }
        }
        return new RecommendationResponse(recentStart, end, List.copyOf(result));
    }

    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
