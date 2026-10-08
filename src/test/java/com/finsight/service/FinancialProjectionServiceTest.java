package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.ScenarioRequest;
import com.finsight.entity.Account;
import com.finsight.entity.AccountType;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.FinancialGoal;
import com.finsight.entity.ScenarioType;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.FinancialGoalRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialProjectionServiceTest {

    @Autowired private FinancialProjectionService service;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private FinancialGoalRepository financialGoalRepository;

    @Test
    void projectsSixMonthAverageWithDisclosedUncertainty() {
        User user = userRepository.save(new User("Drew", "forecast@example.com", "encoded"));
        Account account = account(user);
        seedSixMonths(user, account);

        var forecast = service.forecast(user.getEmail(), 3);

        assertEquals("READY", forecast.status());
        assertEquals(6, forecast.monthsOfHistory());
        assertEquals(new BigDecimal("1000.00"), forecast.monthlyAverageIncome());
        assertEquals(new BigDecimal("400.00"), forecast.monthlyAverageExpenses());
        assertEquals(new BigDecimal("600.00"), forecast.monthlyAverageSurplus());
        assertEquals(3, forecast.projections().size());
        assertEquals(new BigDecimal("15400.00"), forecast.projections().get(2).projectedEndingBalance());
        assertEquals("MODERATE", forecast.confidence());
    }

    @Test
    void estimatesGoalFinishFromEnteredMonthlyPlanAndSeparatesPlanFromRecordedSurplus() {
        User user = userRepository.save(new User("Drew", "goal-forecast@example.com", "encoded"));
        Account account = account(user);
        seedSixMonths(user, account);
        FinancialGoal goal = new FinancialGoal();
        goal.setUser(user); goal.setName("Reserve"); goal.setTargetAmount(new BigDecimal("1000.00"));
        goal.setCurrentAmount(new BigDecimal("500.00")); goal.setPlannedMonthlyContribution(new BigDecimal("100.00"));
        goal.setCurrency("INR"); goal.setActive(true); financialGoalRepository.save(goal);

        var forecast = service.forecast(user.getEmail(), 3);

        assertEquals(5, forecast.goalProjections().get(0).estimatedMonths());
        assertEquals(YearMonth.now().plusMonths(5), forecast.goalProjections().get(0).estimatedCompletionMonth());
        assertEquals("ESTIMATE", forecast.goalProjections().get(0).status());
    }

    @Test
    void scenarioArithmeticUsesObservedCategorySpend() {
        User user = userRepository.save(new User("Drew", "scenario@example.com", "encoded"));
        Account account = account(user);
        seedSixMonths(user, account);

        var scenario = service.scenario(user.getEmail(), new ScenarioRequest(
                ScenarioType.REDUCE_CATEGORY_EXPENSE, "Food", null, new BigDecimal("20.00"), 3));

        assertEquals("READY", scenario.status());
        assertEquals(new BigDecimal("600.00"), scenario.baselineMonthlySurplus());
        assertEquals(new BigDecimal("80.00"), scenario.monthlyChange());
        assertEquals(new BigDecimal("680.00"), scenario.scenarioMonthlySurplus());
        assertEquals(new BigDecimal("240.00"), scenario.projectedCumulativeChange());
    }

    private Account account(User user) {
        Account account = new Account();
        account.setUser(user);
        account.setName("Savings");
        account.setType(AccountType.SAVINGS);
        account.setCurrency("INR");
        account.setOpeningBalance(new BigDecimal("10000.00"));
        account.setActive(true);
        return accountRepository.save(account);
    }

    private void seedSixMonths(User user, Account account) {
        YearMonth firstMonth = YearMonth.now().minusMonths(6);
        for (int index = 0; index < 6; index++) {
            LocalDate date = firstMonth.plusMonths(index).atDay(15);
            transaction(user, account, TransactionType.INCOME, "1000.00", "Salary", date);
            transaction(user, account, TransactionType.EXPENSE, "400.00", "Food", date);
        }
    }

    private void transaction(User user, Account account, TransactionType type, String amount,
                             String category, LocalDate date) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setAccount(account);
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCategory(category);
        transaction.setDescription(category);
        transaction.setCurrency("INR");
        transaction.setTransactionDate(date);
        transactionRepository.save(transaction);
    }
}
