package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.entity.Account;
import com.finsight.entity.AccountType;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ConflictException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void dashboardUsesMonthlyCashFlowAndExcludesFutureTransactionsFromCurrentFigures() {
        User user = userRepository.save(new User("Avery", "dashboard@example.com", "encoded"));
        Account account = account(user, "INR", "100.00");
        LocalDate today = LocalDate.now();

        transaction(user, account, TransactionType.INCOME, "500.00", today.minusMonths(1).withDayOfMonth(10));
        transaction(user, account, TransactionType.EXPENSE, "100.00", today.minusMonths(1).withDayOfMonth(11));
        transaction(user, account, TransactionType.INCOME, "1000.00", today.withDayOfMonth(1));
        transaction(user, account, TransactionType.EXPENSE, "100.00", today.withDayOfMonth(1));
        transaction(user, account, TransactionType.INCOME, "900.00", today.plusDays(2));
        transaction(user, account, TransactionType.EXPENSE, "3000.00", today.plusDays(3));

        var dashboard = dashboardService.getDashboard(user.getEmail());

        assertEquals("INR", dashboard.currency());
        assertEquals(new BigDecimal("1000.00"), dashboard.monthlyIncome());
        assertEquals(new BigDecimal("100.00"), dashboard.monthlyExpenses());
        assertEquals(new BigDecimal("900.00"), dashboard.netCashFlow());
        assertEquals(new BigDecimal("90.00"), dashboard.savingsRate());
        assertEquals(new BigDecimal("1400.00"), dashboard.currentBalance());
        assertEquals(new BigDecimal("200.00"), dashboard.totalExpenses());
        assertEquals(new BigDecimal("100.00"), dashboard.monthlySpending().get(
                today.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))));
        assertEquals(4, dashboard.recentTransactions().size());
    }

    @Test
    void dashboardRejectsAmountsThatWouldMixCurrencies() {
        User user = userRepository.save(new User("Jordan", "currency@example.com", "encoded"));
        account(user, "USD", "100.00");

        assertThrows(ConflictException.class, () -> dashboardService.getDashboard(user.getEmail()));
    }

    @Test
    void accountBalancesApplyTransfersAndIgnoreFutureDatedActivity() {
        User user = userRepository.save(new User("Taylor", "transfers@example.com", "encoded"));
        Account source = account(user, "INR", "500.00");
        Account target = new Account();
        target.setUser(user);
        target.setName("Savings");
        target.setType(AccountType.SAVINGS);
        target.setCurrency("INR");
        target.setOpeningBalance(new BigDecimal("100.00"));
        target.setActive(true);
        target = accountRepository.save(target);

        transfer(user, source, target, "75.00", LocalDate.now());
        transfer(user, source, target, "50.00", LocalDate.now().plusDays(1));

        var balances = accountService.findAll(user.getEmail());
        assertEquals(new BigDecimal("425.00"), balances.get(0).currentBalance());
        assertEquals(new BigDecimal("175.00"), balances.get(1).currentBalance());
    }

    private Account account(User user, String currency, String openingBalance) {
        Account account = new Account();
        account.setUser(user);
        account.setName("Primary");
        account.setType(AccountType.BANK);
        account.setCurrency(currency);
        account.setOpeningBalance(new BigDecimal(openingBalance));
        account.setActive(true);
        return accountRepository.save(account);
    }

    private void transaction(User user, Account account, TransactionType type, String amount, LocalDate date) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setAccount(account);
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCategory(type == TransactionType.INCOME ? "Salary" : "Food");
        transaction.setDescription(type.name());
        transaction.setCurrency("INR");
        transaction.setTransactionDate(date);
        transactionRepository.save(transaction);
    }

    private void transfer(User user, Account source, Account target, String amount, LocalDate date) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setAccount(source);
        transaction.setSourceAccount(source);
        transaction.setTargetAccount(target);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCategory("Transfer");
        transaction.setDescription("Account transfer");
        transaction.setCurrency("INR");
        transaction.setTransactionDate(date);
        transactionRepository.save(transaction);
    }
}
