package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.entity.Account;
import com.finsight.entity.AccountType;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.FinancialGoal;
import com.finsight.entity.Liability;
import com.finsight.entity.LiabilityType;
import com.finsight.entity.User;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.FinancialGoalRepository;
import com.finsight.repository.LiabilityRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialHealthScoreServiceTest {

    @Autowired private FinancialHealthScoreService service;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private FinancialGoalRepository financialGoalRepository;
    @Autowired private LiabilityRepository liabilityRepository;

    @Test
    void computesAnExplainableScoreFromSixCompletedMonths() {
        User user = userRepository.save(new User("Casey", "health-score@example.com", "encoded"));
        Account account = new Account();
        account.setUser(user);
        account.setName("Savings");
        account.setType(AccountType.SAVINGS);
        account.setCurrency("INR");
        account.setOpeningBalance(new BigDecimal("10000.00"));
        account.setActive(true);
        accountRepository.save(account);

        YearMonth firstMonth = YearMonth.now().minusMonths(6);
        for (int i = 0; i < 6; i++) {
            LocalDate date = firstMonth.plusMonths(i).atDay(15);
            transaction(user, account, TransactionType.INCOME, "1000.00", "Salary", date);
            transaction(user, account, TransactionType.EXPENSE, "400.00", "Food", date);
        }

        var result = service.calculate(user.getEmail());

        assertEquals(100, result.score());
        assertEquals("PARTIAL", result.status());
        assertEquals("STRONG", result.rating());
        assertEquals(12, result.transactionsAnalyzed());
        assertEquals(4, result.components().size());
        assertEquals(100, result.components().get("savingsRate").score());
        assertTrue(result.components().get("savingsRate").evidence().contains("60.00%"));
        assertTrue(result.unavailableDimensions().stream().anyMatch(value -> value.startsWith("debtBurden:")));
    }

    @Test
    void doesNotInventScoreWhenHistoryIsInsufficient() {
        User user = userRepository.save(new User("Alex", "new-user@example.com", "encoded"));

        var result = service.calculate(user.getEmail());

        assertNull(result.score());
        assertEquals("INSUFFICIENT_DATA", result.status());
        assertEquals("INSUFFICIENT_DATA", result.rating());
    }

    @Test
    void includesEnteredGoalProgressAndPaymentBurdenAsSeparateEvidenceFactors() {
        User user = userRepository.save(new User("Jordan", "health-planning@example.com", "encoded"));
        YearMonth firstMonth = YearMonth.now().minusMonths(6);
        for (int i = 0; i < 6; i++) {
            LocalDate date = firstMonth.plusMonths(i).atDay(15);
            transaction(user, null, TransactionType.INCOME, "1000.00", "Salary", date);
            transaction(user, null, TransactionType.EXPENSE, "400.00", "Food", date);
        }
        FinancialGoal goal = new FinancialGoal();
        goal.setUser(user); goal.setName("Trip"); goal.setTargetAmount(new BigDecimal("1000.00"));
        goal.setCurrentAmount(new BigDecimal("500.00")); goal.setPlannedMonthlyContribution(BigDecimal.ZERO);
        goal.setCurrency("INR"); goal.setActive(true);
        financialGoalRepository.save(goal);
        Liability liability = new Liability();
        liability.setUser(user); liability.setName("Loan"); liability.setType(LiabilityType.PERSONAL_LOAN);
        liability.setCurrentBalance(new BigDecimal("5000.00")); liability.setAnnualInterestRate(new BigDecimal("12.0000"));
        liability.setMonthlyPayment(new BigDecimal("200.00")); liability.setCurrency("INR");
        liability.setAsOfDate(LocalDate.now()); liability.setActive(true); liabilityRepository.save(liability);

        var result = service.calculate(user.getEmail());

        assertEquals(50, result.components().get("goalProgress").score());
        assertEquals(80, result.components().get("debtBurden").score());
        assertTrue(result.components().get("debtBurden").evidence().contains("20.00%"));
        assertTrue(result.unavailableDimensions().stream().anyMatch(value -> value.startsWith("budgetAdherence:")));
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
