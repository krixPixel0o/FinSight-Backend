package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.Budget;
import com.finsight.entity.BudgetPeriod;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.repository.BudgetRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialRecommendationServiceTest {
    @Autowired private FinancialRecommendationService service;
    @Autowired private UserRepository users;
    @Autowired private TransactionRepository transactions;
    @Autowired private BudgetRepository budgets;

    @Test
    void recommendationIncludesComparablePeriodsAndUserOwnedEvidence() {
        User user = users.save(new User("Casey", "recommend@example.com", "encoded"));
        LocalDate lastMonth = LocalDate.now().withDayOfMonth(1).minusDays(1);
        YearMonth endMonth = YearMonth.from(lastMonth);
        for (int i = 5; i >= 3; i--) {
            save(user, TransactionType.EXPENSE, "Food", new BigDecimal("100.00"), endMonth.minusMonths(i).atDay(10));
        }
        for (int i = 2; i >= 0; i--) {
            YearMonth month = endMonth.minusMonths(i);
            save(user, TransactionType.EXPENSE, "Food", new BigDecimal("200.00"), month.atDay(10));
        }
        User other = users.save(new User("Other", "recommend-other@example.com", "encoded"));
        for (int i = 2; i >= 0; i--) {
            save(other, TransactionType.EXPENSE, "Food", new BigDecimal("9000.00"), endMonth.minusMonths(i).atDay(10));
        }

        var response = service.recommend(user.getEmail());

        assertEquals(endMonth.minusMonths(2).atDay(1), response.periodStart());
        assertEquals(lastMonth, response.periodEnd());
        assertEquals(1, response.recommendations().size());
        assertEquals("CATEGORY_INCREASE", response.recommendations().get(0).type());
        assertTrue(response.recommendations().get(0).evidence().contains("300.00 previously vs 600.00 recently"));
    }

    @Test
    void noTransactionsOrBudgetsReturnsAnEmptyRecommendationList() {
        User user = users.save(new User("No activity", "recommend-empty@example.com", "encoded"));

        var response = service.recommend(user.getEmail());

        assertTrue(response.recommendations().isEmpty());
    }

    @Test
    void activeBudgetNearItsLimitProducesABudgetRecommendation() {
        User user = users.save(new User("Budgeted", "recommend-budget@example.com", "encoded"));
        LocalDate today = LocalDate.now();
        save(user, TransactionType.EXPENSE, "Food", new BigDecimal("80.00"), today);

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("100.00"));
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setStartDate(today.withDayOfMonth(1));
        budget.setEndDate(today.withDayOfMonth(today.lengthOfMonth()));
        budgets.save(budget);

        var response = service.recommend(user.getEmail());

        assertEquals(1, response.recommendations().size());
        assertEquals("BUDGET_THRESHOLD", response.recommendations().get(0).type());
    }

    @Test
    void budgetWithNoRecordedSpendingDoesNotProduceARecommendation() {
        User user = users.save(new User("Unused budget", "recommend-unused-budget@example.com", "encoded"));
        LocalDate today = LocalDate.now();

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("100.00"));
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setStartDate(today.withDayOfMonth(1));
        budget.setEndDate(today.withDayOfMonth(today.lengthOfMonth()));
        budgets.save(budget);

        var response = service.recommend(user.getEmail());

        assertTrue(response.recommendations().isEmpty());
    }

    private void save(User user, TransactionType type, String category, BigDecimal amount, LocalDate date) {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(user);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setCategory(category);
        tx.setDescription(category);
        tx.setCurrency("INR");
        tx.setTransactionDate(date);
        transactions.save(tx);
    }
}
