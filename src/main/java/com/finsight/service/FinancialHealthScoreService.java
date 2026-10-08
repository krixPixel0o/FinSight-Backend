package com.finsight.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.FinancialHealthScoreResponse;
import com.finsight.dto.FinancialHealthScoreResponse.Component;
import com.finsight.entity.Account;
import com.finsight.entity.AccountType;
import com.finsight.entity.Budget;
import com.finsight.entity.User;
import com.finsight.entity.Liability;
import com.finsight.entity.FinancialGoal;
import com.finsight.exception.ConflictException;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.BudgetRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.LiabilityRepository;
import com.finsight.repository.FinancialGoalRepository;

/** Transparent health indicators based on six completed calendar months. */
@Service
public class FinancialHealthScoreService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceService accountBalanceService;
    private final int minimumTransactions;
    private final int minimumActiveMonths;
    private final int savingsWeight;
    private final int expenseControlWeight;
    private final int cashFlowWeight;
    private final int budgetWeight;
    private final int emergencyWeight;
    private final int debtWeight;
    private final int goalWeight;
    private final LiabilityRepository liabilityRepository;
    private final FinancialGoalRepository financialGoalRepository;
    private final BigDecimal targetEmergencyMonths;

    public FinancialHealthScoreService(UserRepository userRepository,
            TransactionRepository transactionRepository, BudgetRepository budgetRepository,
            AccountRepository accountRepository, AccountBalanceService accountBalanceService,
            LiabilityRepository liabilityRepository, FinancialGoalRepository financialGoalRepository,
            @Value("${finsight.health-score.minimum-transactions:5}") int minimumTransactions,
            @Value("${finsight.health-score.minimum-active-months:3}") int minimumActiveMonths,
            @Value("${finsight.health-score.weights.savings:20}") int savingsWeight,
            @Value("${finsight.health-score.weights.expense-control:15}") int expenseControlWeight,
            @Value("${finsight.health-score.weights.cash-flow:15}") int cashFlowWeight,
            @Value("${finsight.health-score.weights.budget:10}") int budgetWeight,
            @Value("${finsight.health-score.weights.emergency-fund:15}") int emergencyWeight,
            @Value("${finsight.health-score.weights.debt-burden:15}") int debtWeight,
            @Value("${finsight.health-score.weights.goal-progress:10}") int goalWeight,
            @Value("${finsight.health-score.target-emergency-months:6}") BigDecimal targetEmergencyMonths) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.liabilityRepository = liabilityRepository;
        this.financialGoalRepository = financialGoalRepository;
        this.accountBalanceService = accountBalanceService;
        this.minimumTransactions = minimumTransactions;
        this.minimumActiveMonths = minimumActiveMonths;
        this.savingsWeight = savingsWeight;
        this.expenseControlWeight = expenseControlWeight;
        this.cashFlowWeight = cashFlowWeight;
        this.budgetWeight = budgetWeight;
        this.emergencyWeight = emergencyWeight;
        this.debtWeight = debtWeight;
        this.goalWeight = goalWeight;
        this.targetEmergencyMonths = targetEmergencyMonths;
        validateConfiguration();
    }

    @Transactional(readOnly = true)
    public FinancialHealthScoreResponse calculate(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate today = LocalDate.now();
        LocalDate periodEnd = today.withDayOfMonth(1).minusDays(1);
        LocalDate periodStart = YearMonth.from(periodEnd).minusMonths(5).atDay(1);

        if (accountRepository.countByUserIdAndCurrencyNot(user.getId(), user.getCurrency()) > 0
                || transactionRepository.countByUserIdAndCurrencyNot(user.getId(), user.getCurrency()) > 0) {
            throw new ConflictException("Financial health score is unavailable while account currencies differ from your profile currency");
        }

        List<MonthData> months = monthlyData(user.getId(), periodStart, periodEnd);
        long transactions = transactionRepository.countFinancialTransactionsBetween(user.getId(), periodStart, periodEnd);
        long activeMonths = months.stream().filter(month -> month.income().signum() > 0 || month.expenses().signum() > 0).count();
        BigDecimal income = months.stream().map(MonthData::income).reduce(ZERO, BigDecimal::add);
        BigDecimal expenses = months.stream().map(MonthData::expenses).reduce(ZERO, BigDecimal::add);

        Map<String, Component> components = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();

        BigDecimal savingsRate = income.signum() == 0 ? ZERO
                : income.subtract(expenses).multiply(HUNDRED).divide(income, 2, RoundingMode.HALF_UP);
        int savingsScore = score(savingsRate.add(BigDecimal.valueOf(5)).multiply(BigDecimal.valueOf(4)));
        components.put("savingsRate", new Component(savingsScore, savingsWeight,
                "Measures the six-month income remaining after recorded expenses. Scores reach full marks at a 20% savings rate; rates at or below -5% score zero.",
                "Income " + income.toPlainString() + "; expenses " + expenses.toPlainString() + "; savings rate " + savingsRate.toPlainString() + "%"));

        int expenseControlScore = expenseControl(months);
        BigDecimal earlyAverage = average(months.subList(0, 3));
        BigDecimal recentAverage = average(months.subList(3, 6));
        components.put("expenseControl", new Component(expenseControlScore, expenseControlWeight,
                "Compares average expenses in the latest three completed months with the preceding three. Growth is penalized at two score points per 1% increase; declines are not rewarded beyond 100.",
                "Earlier monthly average " + earlyAverage.toPlainString() + "; recent monthly average " + recentAverage.toPlainString()));

        List<MonthData> monthsWithActivity = months.stream()
                .filter(month -> month.income().signum() > 0 || month.expenses().signum() > 0).toList();
        long positiveMonths = monthsWithActivity.stream()
                .filter(month -> month.income().compareTo(month.expenses()) >= 0).count();
        int cashFlowScore = BigDecimal.valueOf(positiveMonths).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(Math.max(1, monthsWithActivity.size())), 0, RoundingMode.HALF_UP).intValue();
        components.put("cashFlowStability", new Component(cashFlowScore, cashFlowWeight,
                "Share of the six months where recorded income met or exceeded expenses.",
                positiveMonths + " of " + monthsWithActivity.size() + " months with activity had non-negative recorded cash flow"));

        List<Budget> activeBudgets = budgetRepository.findByUserIdOrderByEndDateAsc(user.getId()).stream()
                .filter(budget -> !budget.getStartDate().isAfter(today) && !budget.getEndDate().isBefore(today))
                .toList();
        if (activeBudgets.isEmpty()) {
            unavailable.add("budgetAdherence: no active budgets");
        } else {
            BigDecimal totalUtilization = ZERO;
            for (Budget budget : activeBudgets) {
                BigDecimal spent = transactionRepository.sumExpenses(user.getId(), budget.getCategory(), budget.getStartDate(), today);
                BigDecimal utilization = safe(spent).multiply(HUNDRED).divide(budget.getAmount(), 4, RoundingMode.HALF_UP);
                totalUtilization = totalUtilization.add(utilization);
            }
            BigDecimal averageUtilization = totalUtilization.divide(BigDecimal.valueOf(activeBudgets.size()), 4, RoundingMode.HALF_UP);
            int budgetScore = score(BigDecimal.valueOf(100).subtract(averageUtilization.subtract(BigDecimal.valueOf(75)).max(ZERO).multiply(BigDecimal.valueOf(4))));
            components.put("budgetAdherence", new Component(budgetScore, budgetWeight,
                    "Average progress against active budgets. Up to 75% utilization scores full marks; 100% utilization scores zero.",
                    activeBudgets.size() + " active budgets; average utilization " + averageUtilization.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"));
        }

        List<Account> reserveAccounts = accountRepository.findByUserIdAndActiveTrueOrderByName(user.getId()).stream()
                .filter(account -> account.getType() == AccountType.BANK || account.getType() == AccountType.SAVINGS
                        || account.getType() == AccountType.CASH || account.getType() == AccountType.WALLET)
                .toList();
        BigDecimal monthlyExpenseAverage = expenses.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        if (reserveAccounts.isEmpty() || monthlyExpenseAverage.signum() <= 0) {
            unavailable.add("emergencyFund: cash-like account or expense history is unavailable");
        } else {
            Map<Long, BigDecimal> balances = accountBalanceService.balances(reserveAccounts, today);
            BigDecimal reserve = reserveAccounts.stream().map(account -> safe(balances.get(account.getId())))
                    .reduce(ZERO, BigDecimal::add).max(ZERO);
            BigDecimal coverageMonths = reserve.divide(monthlyExpenseAverage, 2, RoundingMode.HALF_UP);
            int reserveScore = score(coverageMonths.multiply(HUNDRED).divide(targetEmergencyMonths, 2, RoundingMode.HALF_UP));
            components.put("emergencyFund", new Component(reserveScore, emergencyWeight,
                    "Cash-like active account balances divided by average monthly expenses. Full marks require the configured reserve target.",
                    "Cash-like balances " + reserve.toPlainString() + "; average monthly expenses " + monthlyExpenseAverage.toPlainString()
                            + "; coverage " + coverageMonths.toPlainString() + " months; target " + targetEmergencyMonths.toPlainString()));
        }

        List<Liability> activeLiabilities = liabilityRepository.findByUserIdAndActiveTrue(user.getId());
        if (activeLiabilities.isEmpty()) {
            unavailable.add("debtBurden: no active liability records");
        } else if (activeLiabilities.stream().anyMatch(item -> !item.getCurrency().equalsIgnoreCase(user.getCurrency()))) {
            unavailable.add("debtBurden: liabilities use currencies outside the profile currency");
        } else if (income.signum() <= 0) {
            unavailable.add("debtBurden: recorded income is unavailable for the six-month period");
        } else {
            BigDecimal monthlyPayments = activeLiabilities.stream().map(Liability::getMonthlyPayment)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal monthlyIncomeAverage = income.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
            BigDecimal paymentBurdenPercent = monthlyPayments.multiply(HUNDRED)
                    .divide(monthlyIncomeAverage, 2, RoundingMode.HALF_UP);
            int debtScore = score(HUNDRED.subtract(paymentBurdenPercent.subtract(BigDecimal.valueOf(15))
                    .max(ZERO).multiply(BigDecimal.valueOf(4))));
            BigDecimal balance = activeLiabilities.stream().map(Liability::getCurrentBalance)
                    .reduce(ZERO, BigDecimal::add);
            components.put("debtBurden", new Component(debtScore, debtWeight,
                    "Scores recorded required monthly liability payments against average recorded monthly income. Full marks are at 15% or lower; zero at 40% or higher. This does not assess interest cost or lender-verified balances.",
                    activeLiabilities.size() + " active liabilities; reported balances " + balance.toPlainString()
                            + "; monthly payments " + monthlyPayments.toPlainString() + "; payment burden "
                            + paymentBurdenPercent.toPlainString() + "% of average monthly income " + monthlyIncomeAverage.toPlainString()));
        }

        List<FinancialGoal> activeGoals = financialGoalRepository.findByUserIdAndActiveTrue(user.getId());
        if (activeGoals.isEmpty()) {
            unavailable.add("goalProgress: no active financial goals");
        } else if (activeGoals.stream().anyMatch(item -> !item.getCurrency().equalsIgnoreCase(user.getCurrency()))) {
            unavailable.add("goalProgress: goals use currencies outside the profile currency");
        } else {
            BigDecimal averageProgress = activeGoals.stream().map(goal -> goal.getCurrentAmount().multiply(HUNDRED)
                    .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP).min(HUNDRED))
                    .reduce(ZERO, BigDecimal::add).divide(BigDecimal.valueOf(activeGoals.size()), 2, RoundingMode.HALF_UP);
            int goalScore = score(averageProgress);
            components.put("goalProgress", new Component(goalScore, goalWeight,
                    "Average saved-to-target progress across active goals. This measures entered balances and targets only; it does not verify that saved money is held in a separate account or assess deadline feasibility.",
                    activeGoals.size() + " active goals; average progress " + averageProgress.toPlainString() + "%"));
        }

        boolean enoughData = transactions >= minimumTransactions && activeMonths >= minimumActiveMonths;
        int totalWeight = components.values().stream().mapToInt(Component::weight).sum();
        int weightedPoints = components.values().stream().mapToInt(component -> component.score() * component.weight()).sum();
        Integer totalScore = enoughData && totalWeight > 0
                ? BigDecimal.valueOf(weightedPoints).divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.HALF_UP).intValue()
                : null;
        String status = !enoughData ? "INSUFFICIENT_DATA" : unavailable.isEmpty() ? "READY" : "PARTIAL";
        return new FinancialHealthScoreResponse(totalScore, status, rating(totalScore), user.getCurrency(),
                periodStart, periodEnd, transactions, Map.copyOf(components), List.copyOf(unavailable));
    }

    private List<MonthData> monthlyData(Long userId, LocalDate start, LocalDate end) {
        List<MonthData> result = new ArrayList<>();
        YearMonth month = YearMonth.from(start);
        for (int index = 0; index < 6; index++, month = month.plusMonths(1)) {
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = month.atEndOfMonth();
            BigDecimal income = transactionRepository.sumByTypeBetween(userId, com.finsight.entity.TransactionType.INCOME, monthStart, monthEnd);
            BigDecimal expenses = transactionRepository.sumByTypeBetween(userId, com.finsight.entity.TransactionType.EXPENSE, monthStart, monthEnd);
            result.add(new MonthData(safe(income), safe(expenses)));
        }
        return result;
    }

    private int expenseControl(List<MonthData> months) {
        BigDecimal previous = average(months.subList(0, 3));
        BigDecimal recent = average(months.subList(3, 6));
        if (previous.signum() == 0) return recent.signum() == 0 ? 100 : 0;
        BigDecimal growthPercent = recent.subtract(previous).multiply(HUNDRED).divide(previous, 4, RoundingMode.HALF_UP);
        return score(HUNDRED.subtract(growthPercent.max(ZERO).multiply(BigDecimal.valueOf(2))));
    }

    private BigDecimal average(List<MonthData> months) {
        return months.stream().map(MonthData::expenses).reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    private int score(BigDecimal value) {
        return value.max(ZERO).min(HUNDRED).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String rating(Integer score) {
        if (score == null) return "INSUFFICIENT_DATA";
        if (score >= 80) return "STRONG";
        if (score >= 60) return "STABLE";
        if (score >= 40) return "NEEDS_ATTENTION";
        return "AT_RISK";
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private void validateConfiguration() {
        long configuredWeightTotal = (long) savingsWeight + expenseControlWeight + cashFlowWeight + budgetWeight
                + emergencyWeight + debtWeight + goalWeight;
        if (minimumTransactions < 1 || minimumActiveMonths < 1 || minimumActiveMonths > 6
                || savingsWeight < 0 || expenseControlWeight < 0 || cashFlowWeight < 0
                || budgetWeight < 0 || emergencyWeight < 0 || debtWeight < 0 || goalWeight < 0
                || targetEmergencyMonths.signum() <= 0 || configuredWeightTotal != 100) {
            throw new IllegalArgumentException("Financial health score weights must be non-negative and add up to 100");
        }
    }

    private record MonthData(BigDecimal income, BigDecimal expenses) { }
}
