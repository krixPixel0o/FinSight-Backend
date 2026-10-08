package com.finsight.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.DashboardResponse;
import com.finsight.dto.TransactionResponse;
import com.finsight.entity.Account;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.exception.ConflictException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@Service
public class DashboardService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final FinancialMathService financialMathService;
    private final AccountBalanceService accountBalanceService;

    public DashboardService(TransactionRepository transactionRepository, UserRepository userRepository,
                            AccountRepository accountRepository, FinancialMathService financialMathService,
                            AccountBalanceService accountBalanceService) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.financialMathService = financialMathService;
        this.accountBalanceService = accountBalanceService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        if (accountRepository.countByUserIdAndCurrencyNot(user.getId(), user.getCurrency()) > 0
                || transactionRepository.countByUserIdAndCurrencyNot(user.getId(), user.getCurrency()) > 0) {
            throw new ConflictException(
                    "Account and transaction currencies differ. Reconcile them to " + user.getCurrency()
                            + " before viewing combined totals.");
        }

        BigDecimal totalIncome = value(transactionRepository.sumByTypeToDate(user.getId(), TransactionType.INCOME, today));
        BigDecimal totalExpenses = value(transactionRepository.sumByTypeToDate(user.getId(), TransactionType.EXPENSE, today));
        BigDecimal monthlyIncome = value(transactionRepository.sumByTypeBetween(user.getId(), TransactionType.INCOME, monthStart, today));
        BigDecimal monthlyExpenses = value(transactionRepository.sumByTypeBetween(user.getId(), TransactionType.EXPENSE, monthStart, today));
        BigDecimal netCashFlow = financialMathService.calculateNetCashFlow(monthlyIncome, monthlyExpenses);
        BigDecimal currentBalance = calculateCurrentBalance(user.getId(), today);
        BigDecimal savingsRate = financialMathService.calculateSavingsRate(monthlyIncome, monthlyExpenses);

        LinkedHashMap<String, BigDecimal> categorySpending = new LinkedHashMap<>();
        transactionRepository.sumByCategoryBetween(user.getId(), TransactionType.EXPENSE, monthStart, today)
                .forEach(row -> categorySpending.put((String) row[0], value((BigDecimal) row[1])));

        Map<String, BigDecimal> monthlySpending = buildMonthlySpending(user.getId());
        List<TransactionResponse> recentTransactions = transactionRepository
                .findByUserIdAndTransactionDateLessThanEqualOrderByTransactionDateDescCreatedAtDesc(
                        user.getId(), today, PageRequest.of(0, 8))
                .map(TransactionResponse::from).getContent();
        List<String> insights = buildInsights(categorySpending, monthlyExpenses, savingsRate);

        return new DashboardResponse(
                user.getCurrency(),
                currentBalance,
                totalIncome,
                totalExpenses,
                monthlyIncome,
                monthlyExpenses,
                netCashFlow,
                savingsRate,
                categorySpending,
                monthlySpending,
                recentTransactions,
                insights);
    }

    private BigDecimal calculateCurrentBalance(Long userId, LocalDate asOfDate) {
        BigDecimal totalBalance = BigDecimal.ZERO;
        List<Account> accounts = accountRepository.findByUserIdAndActiveTrueOrderByName(userId);
        Map<Long, BigDecimal> balances = accountBalanceService.balances(accounts, asOfDate);
        for (Account account : accounts) totalBalance = totalBalance.add(value(balances.get(account.getId())));
        return totalBalance;
    }

    private Map<String, BigDecimal> buildMonthlySpending(Long userId) {
        LocalDate today = LocalDate.now();
        LinkedHashMap<String, BigDecimal> monthlySpending = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = today.minusMonths(i).withDayOfMonth(1);
            String label = monthDate.format(formatter);
            LocalDate monthEnd = monthDate.withDayOfMonth(monthDate.lengthOfMonth());
            if (monthEnd.isAfter(today)) {
                monthEnd = today;
            }
            BigDecimal amount = value(transactionRepository.sumByTypeBetween(userId, TransactionType.EXPENSE, monthDate, monthEnd));
            monthlySpending.put(label, amount);
        }

        return monthlySpending;
    }

    private List<String> buildInsights(Map<String, BigDecimal> categorySpending, BigDecimal expenses, BigDecimal savingsRate) {
        if (categorySpending.isEmpty()) {
            return List.of("Add a few transactions to start generating cash-flow insights.");
        }

        var entry = categorySpending.entrySet().iterator().next();
        String topCategory = entry.getKey();
        BigDecimal topAmount = value(entry.getValue());
        BigDecimal share = expenses.signum() == 0 ? BigDecimal.ZERO : topAmount.multiply(BigDecimal.valueOf(100)).divide(expenses, 0, RoundingMode.HALF_UP);

        return List.of(
                "Your highest spending category this month is " + topCategory + ".",
                topCategory + " represents " + share + "% of this month's expenses.",
                "Your savings rate this month is " + savingsRate + "%.");
    }

    private BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
