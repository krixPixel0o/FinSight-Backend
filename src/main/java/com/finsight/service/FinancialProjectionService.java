package com.finsight.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.ForecastResponse;
import com.finsight.dto.ForecastResponse.BudgetRunway;
import com.finsight.dto.ForecastResponse.Projection;
import com.finsight.dto.ForecastResponse.GoalProjection;
import com.finsight.dto.ScenarioRequest;
import com.finsight.dto.ScenarioResponse;
import com.finsight.entity.Account;
import com.finsight.entity.Budget;
import com.finsight.entity.FinancialGoal;
import com.finsight.entity.ScenarioType;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ConflictException;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.BudgetRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.FinancialGoalRepository;

/** Deterministic projections using historical averages; no model-generated arithmetic. */
@Service
public class FinancialProjectionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int LOOKBACK_MONTHS = 6;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetRepository budgetRepository;
    private final AccountBalanceService accountBalanceService;
    private final FinancialGoalRepository financialGoalRepository;

    public FinancialProjectionService(UserRepository userRepository, TransactionRepository transactionRepository,
            AccountRepository accountRepository, BudgetRepository budgetRepository,
            AccountBalanceService accountBalanceService, FinancialGoalRepository financialGoalRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.budgetRepository = budgetRepository;
        this.accountBalanceService = accountBalanceService;
        this.financialGoalRepository = financialGoalRepository;
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecast(String email, int horizonMonths) {
        if (horizonMonths < 1 || horizonMonths > 12) {
            throw new IllegalArgumentException("Forecast horizon must be between 1 and 12 months");
        }
        User user = findUser(email);
        ensureSingleCurrency(user);
        LocalDate today = LocalDate.now();
        YearMonth lastClosedMonth = YearMonth.from(today).minusMonths(1);
        YearMonth firstMonth = lastClosedMonth.minusMonths(LOOKBACK_MONTHS - 1L);
        List<MonthData> months = months(user.getId(), firstMonth, LOOKBACK_MONTHS);
        long activeMonths = months.stream().filter(MonthData::hasActivity).count();
        long incomeMonths = months.stream().filter(month -> month.income().signum() > 0).count();
        long expenseMonths = months.stream().filter(month -> month.expenses().signum() > 0).count();
        BigDecimal incomeSum = months.stream().map(MonthData::income).reduce(ZERO, BigDecimal::add);
        BigDecimal expenseSum = months.stream().map(MonthData::expenses).reduce(ZERO, BigDecimal::add);
        BigDecimal averageIncome = incomeMonths >= 3 ? average(incomeSum, LOOKBACK_MONTHS) : null;
        BigDecimal averageExpenses = expenseMonths >= 3 ? average(expenseSum, LOOKBACK_MONTHS) : null;
        BigDecimal averageSurplus = averageIncome != null && averageExpenses != null
                ? averageIncome.subtract(averageExpenses) : null;
        BigDecimal flowDeviation = averageIncome != null && averageExpenses != null
                ? netFlowStandardDeviation(months) : null;

        List<Account> accounts = accountRepository.findByUserIdAndActiveTrueOrderByName(user.getId());
        Map<Long, BigDecimal> balances = accountBalanceService.balances(accounts, today);
        BigDecimal currentBalance = accounts.stream().map(account -> safe(balances.get(account.getId())))
                .reduce(ZERO, BigDecimal::add);

        List<Projection> projections = new ArrayList<>();
        if (activeMonths >= 3 && averageIncome != null && averageExpenses != null) {
            BigDecimal runningBalance = currentBalance;
            for (int monthOffset = 1; monthOffset <= horizonMonths; monthOffset++) {
                YearMonth targetMonth = YearMonth.from(today).plusMonths(monthOffset);
                runningBalance = runningBalance.add(averageSurplus);
                projections.add(new Projection(targetMonth, averageIncome, averageExpenses,
                        averageSurplus, runningBalance));
            }
        }

        boolean enoughHistory = activeMonths >= 3;
        String status = !enoughHistory ? "INSUFFICIENT_DATA"
                : projections.size() < horizonMonths ? "PARTIAL" : "READY";
        String confidence = confidence(activeMonths, flowDeviation, averageSurplus);
        List<String> unavailable = new ArrayList<>();
        if (incomeMonths < 3) unavailable.add("Future income: fewer than three income-active months in the lookback window.");
        if (expenseMonths < 3) unavailable.add("Future expenses: fewer than three expense-active months in the lookback window.");
        List<GoalProjection> goalProjections = new ArrayList<>();
        for (FinancialGoal goal : financialGoalRepository.findByUserIdAndActiveTrue(user.getId())) {
            if (!goal.getCurrency().equalsIgnoreCase(user.getCurrency())) {
                goalProjections.add(new GoalProjection(goal.getId(), goal.getName(), null,
                        goal.getPlannedMonthlyContribution(), null, null, "CURRENCY_MISMATCH"));
                continue;
            }
            BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount()).max(ZERO);
            if (remaining.signum() == 0) {
                goalProjections.add(new GoalProjection(goal.getId(), goal.getName(), ZERO,
                        goal.getPlannedMonthlyContribution(), 0, YearMonth.from(today), "COMPLETE"));
                continue;
            }
            BigDecimal contribution = goal.getPlannedMonthlyContribution();
            if (contribution.signum() <= 0) {
                goalProjections.add(new GoalProjection(goal.getId(), goal.getName(), remaining,
                        contribution, null, null, "NO_MONTHLY_CONTRIBUTION"));
                continue;
            }
            BigDecimal monthCount = remaining.divide(contribution, 0, RoundingMode.CEILING);
            if (monthCount.compareTo(BigDecimal.valueOf(1200)) > 0) {
                goalProjections.add(new GoalProjection(goal.getId(), goal.getName(), remaining,
                        contribution, null, null, "BEYOND_100_YEARS"));
                continue;
            }
            int monthsToCompletion = monthCount.intValue();
            String goalStatus = averageSurplus == null ? "NO_CASH_FLOW_BASELINE"
                    : averageSurplus.signum() <= 0 ? "NON_POSITIVE_HISTORICAL_SURPLUS"
                    : contribution.compareTo(averageSurplus) > 0 ? "CONTRIBUTION_ABOVE_HISTORICAL_SURPLUS" : "ESTIMATE";
            goalProjections.add(new GoalProjection(goal.getId(), goal.getName(), remaining, contribution,
                    monthsToCompletion, YearMonth.from(today).plusMonths(monthsToCompletion), goalStatus));
        }
        if (goalProjections.isEmpty()) unavailable.add("Goal completion: no active goals are recorded.");

        List<String> assumptions = List.of(
                "Uses the arithmetic mean of the previous six fully completed calendar months; future seasonality and one-off events are not modeled.",
                "Projected balances assume recorded income and expenses repeat monthly; transfers are neutral in the combined account balance.",
                "Uncertainty is summarized by historical monthly net-flow standard deviation and is not a probability guarantee.",
                "Goal completion dates assume the entered monthly contribution continues unchanged; each goal is considered separately and returns, fees, and competing goals are not modeled.");
        return new ForecastResponse(status, user.getCurrency(), "SIX_COMPLETED_MONTH_ARITHMETIC_MEAN",
                confidence, Math.toIntExact(activeMonths), averageIncome, averageExpenses,
                averageSurplus, flowDeviation, currentBalance, List.copyOf(projections),
                budgetRunways(user.getId(), today), List.copyOf(goalProjections), assumptions, List.copyOf(unavailable));
    }

    @Transactional(readOnly = true)
    public ScenarioResponse scenario(String email, ScenarioRequest request) {
        User user = findUser(email);
        ensureSingleCurrency(user);
        validateScenario(request);
        int horizon = request.horizonMonths() == null ? 12 : request.horizonMonths();
        LocalDate today = LocalDate.now();
        YearMonth endMonth = YearMonth.from(today).minusMonths(1);
        YearMonth startMonth = endMonth.minusMonths(2);
        LocalDate start = startMonth.atDay(1);
        BigDecimal income = ZERO;
        BigDecimal expenses = ZERO;
        BigDecimal monthlyChange;
        String category = request.category() == null ? null : request.category().trim();
        int supportedMonths = 0;

        for (int index = 0; index < 3; index++) {
            YearMonth month = startMonth.plusMonths(index);
            BigDecimal monthIncome = safe(transactionRepository.sumByTypeBetween(user.getId(), TransactionType.INCOME,
                    month.atDay(1), month.atEndOfMonth()));
            BigDecimal monthExpenses = safe(transactionRepository.sumByTypeBetween(user.getId(), TransactionType.EXPENSE,
                    month.atDay(1), month.atEndOfMonth()));
            income = income.add(monthIncome);
            expenses = expenses.add(monthExpenses);
            if (monthIncome.signum() > 0 || monthExpenses.signum() > 0) supportedMonths++;
        }
        BigDecimal averageIncome = average(income, 3);
        BigDecimal averageExpenses = average(expenses, 3);
        BigDecimal categoryAverage = ZERO;
        int categoryActiveMonths = 0;
        if (request.type() == ScenarioType.REDUCE_CATEGORY_EXPENSE) {
            for (int index = 0; index < 3; index++) {
                YearMonth month = startMonth.plusMonths(index);
                BigDecimal value = safe(transactionRepository.sumExpenses(user.getId(), category,
                        month.atDay(1), month.atEndOfMonth()));
                categoryAverage = categoryAverage.add(value);
                if (value.signum() > 0) categoryActiveMonths++;
            }
            categoryAverage = average(categoryAverage, 3);
        }

        if (supportedMonths < 2 || (request.type() == ScenarioType.REDUCE_CATEGORY_EXPENSE && categoryActiveMonths < 2)) {
            return new ScenarioResponse("INSUFFICIENT_DATA", request.type().name(), user.getCurrency(), category,
                    horizon, averageIncome, averageExpenses, averageIncome.subtract(averageExpenses), null,
                    null, null, List.of("Requires activity in at least two of the three fully completed baseline months."));
        }

        monthlyChange = switch (request.type()) {
            case REDUCE_CATEGORY_EXPENSE -> categoryAverage.multiply(request.percentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case REDUCE_INCOME -> averageIncome.multiply(request.percentage())
                    .divide(BigDecimal.valueOf(-100), 2, RoundingMode.HALF_UP);
            case ADD_MONTHLY_OUTFLOW -> request.monthlyAmount().negate();
        };
        BigDecimal baselineSurplus = averageIncome.subtract(averageExpenses);
        BigDecimal scenarioSurplus = baselineSurplus.add(monthlyChange);
        BigDecimal cumulativeChange = monthlyChange.multiply(BigDecimal.valueOf(horizon));
        return new ScenarioResponse("READY", request.type().name(), user.getCurrency(), category, horizon,
                averageIncome, averageExpenses, baselineSurplus, monthlyChange, scenarioSurplus,
                cumulativeChange, List.of(
                        "Baseline uses the previous three fully completed calendar months, including zero-spend months.",
                        "Assumes the requested percentage or monthly amount remains constant for the full horizon.",
                        "This is arithmetic scenario analysis only; it does not estimate investment returns, taxes, or behavior changes."));
    }

    private List<BudgetRunway> budgetRunways(Long userId, LocalDate today) {
        LocalDate recentStart = YearMonth.from(today).minusMonths(3).atDay(1);
        List<BudgetRunway> runways = new ArrayList<>();
        for (Budget budget : budgetRepository.findByUserIdOrderByEndDateAsc(userId)) {
            if (budget.getStartDate().isAfter(today) || budget.getEndDate().isBefore(today)) continue;
            BigDecimal spent = safe(transactionRepository.sumExpenses(userId, budget.getCategory(), budget.getStartDate(), today));
            BigDecimal remaining = budget.getAmount().subtract(spent);
            BigDecimal historicalSpend = safe(transactionRepository.sumExpenses(userId, budget.getCategory(), recentStart,
                    today.isBefore(recentStart) ? recentStart : today));
            BigDecimal monthlyAverage = average(historicalSpend, 3);
            BigDecimal monthsToLimit = monthlyAverage.signum() <= 0 ? null
                    : remaining.max(ZERO).divide(monthlyAverage, 2, RoundingMode.HALF_UP);
            String status = remaining.signum() <= 0 ? "LIMIT_REACHED"
                    : monthsToLimit == null ? "NO_RECENT_SPEND_BASELINE" : "ESTIMATE";
            runways.add(new BudgetRunway(budget.getId(), budget.getCategory(), remaining,
                    monthlyAverage, monthsToLimit, status));
        }
        return List.copyOf(runways);
    }

    private List<MonthData> months(Long userId, YearMonth start, int count) {
        List<MonthData> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            YearMonth month = start.plusMonths(index);
            BigDecimal income = safe(transactionRepository.sumByTypeBetween(userId, TransactionType.INCOME,
                    month.atDay(1), month.atEndOfMonth()));
            BigDecimal expenses = safe(transactionRepository.sumByTypeBetween(userId, TransactionType.EXPENSE,
                    month.atDay(1), month.atEndOfMonth()));
            result.add(new MonthData(income, expenses));
        }
        return result;
    }

    private BigDecimal netFlowStandardDeviation(List<MonthData> months) {
        BigDecimal average = months.stream().map(month -> month.income().subtract(month.expenses()))
                .reduce(ZERO, BigDecimal::add).divide(BigDecimal.valueOf(months.size()), 8, RoundingMode.HALF_UP);
        BigDecimal variance = months.stream().map(month -> month.income().subtract(month.expenses()).subtract(average).pow(2))
                .reduce(ZERO, BigDecimal::add).divide(BigDecimal.valueOf(months.size()), 8, RoundingMode.HALF_UP);
        return variance.sqrt(MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP);
    }

    private String confidence(long activeMonths, BigDecimal deviation, BigDecimal averageSurplus) {
        if (activeMonths < 3) return "INSUFFICIENT_DATA";
        if (deviation == null || averageSurplus == null) return "LOW";
        BigDecimal scale = averageSurplus.abs().max(BigDecimal.ONE);
        BigDecimal variability = deviation.divide(scale, 4, RoundingMode.HALF_UP);
        if (activeMonths >= 5 && variability.compareTo(new BigDecimal("0.25")) <= 0) return "MODERATE";
        if (variability.compareTo(new BigDecimal("0.75")) > 0) return "LOW";
        return "CAUTIOUS";
    }

    private BigDecimal average(BigDecimal total, int months) {
        return total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    private void validateScenario(ScenarioRequest request) {
        if (request == null || request.type() == null) throw new IllegalArgumentException("Scenario type is required");
        int horizon = request.horizonMonths() == null ? 12 : request.horizonMonths();
        if (horizon < 1 || horizon > 24) throw new IllegalArgumentException("Scenario horizon must be between 1 and 24 months");
        switch (request.type()) {
            case REDUCE_CATEGORY_EXPENSE -> {
                if (request.category() == null || request.category().isBlank()) throw new IllegalArgumentException("Expense category is required");
                validatePercentage(request.percentage());
            }
            case REDUCE_INCOME -> validatePercentage(request.percentage());
            case ADD_MONTHLY_OUTFLOW -> {
                if (request.monthlyAmount() == null || request.monthlyAmount().signum() <= 0) {
                    throw new IllegalArgumentException("Monthly outflow must be greater than zero");
                }
            }
        }
    }

    private void validatePercentage(BigDecimal percentage) {
        if (percentage == null || percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Scenario percentage must be between 0 and 100");
        }
    }

    private void ensureSingleCurrency(User user) {
        if (accountRepository.countByUserIdAndCurrencyNot(user.getId(), user.getCurrency()) > 0
                || transactionRepository.countByUserIdAndCurrencyNot(user.getId(), user.getCurrency()) > 0) {
            throw new ConflictException("Financial projections are unavailable while account currencies differ from your profile currency");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private record MonthData(BigDecimal income, BigDecimal expenses) {
        boolean hasActivity() { return income.signum() > 0 || expenses.signum() > 0; }
    }
}
