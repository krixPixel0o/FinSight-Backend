package com.finsight.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

@Service
public class FinancialMathService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public BigDecimal calculateNetCashFlow(BigDecimal income, BigDecimal expense) {
        return safe(income).subtract(safe(expense));
    }

    public BigDecimal calculateSavingsRate(BigDecimal income, BigDecimal expense) {
        BigDecimal totalIncome = safe(income);
        if (totalIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = calculateNetCashFlow(totalIncome, expense);
        return net.multiply(HUNDRED)
                .divide(totalIncome, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateAccountBalance(BigDecimal openingBalance, BigDecimal incoming, BigDecimal outgoing, BigDecimal transferIncoming) {
        return safe(openingBalance)
                .add(safe(incoming))
                .subtract(safe(outgoing))
                .add(safe(transferIncoming));
    }

    public BigDecimal calculateBudgetUtilization(BigDecimal limit, BigDecimal spent) {
        BigDecimal safeLimit = safe(limit);
        if (safeLimit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return safe(spent).multiply(HUNDRED)
                .divide(safeLimit, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateBudgetRemaining(BigDecimal limit, BigDecimal spent) {
        return safe(limit).subtract(safe(spent));
    }

    public String evaluateBudgetStatus(BigDecimal limit, BigDecimal spent) {
        BigDecimal remaining = calculateBudgetRemaining(limit, spent);
        return remaining.signum() < 0 ? "OVER_BUDGET" : "ON_TRACK";
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
