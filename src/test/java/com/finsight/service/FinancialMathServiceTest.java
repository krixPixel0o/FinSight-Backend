package com.finsight.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class FinancialMathServiceTest {

    private final FinancialMathService service = new FinancialMathService();

    @Test
    void incomeMinusExpensesProducesNetCashFlow() {
        assertEquals(new BigDecimal("2500.00"), service.calculateNetCashFlow(new BigDecimal("5000.00"), new BigDecimal("2500.00")));
    }

    @Test
    void transferDoesNotChangeNetCashFlow() {
        BigDecimal netFlow = service.calculateNetCashFlow(
                new BigDecimal("8000.00"),
                new BigDecimal("5000.00")
        );
        assertEquals(new BigDecimal("3000.00"), netFlow);
    }

    @Test
    void zeroIncomeDoesNotCreateInvalidSavingsRate() {
        assertEquals(BigDecimal.ZERO, service.calculateSavingsRate(BigDecimal.ZERO, new BigDecimal("200.00")));
    }

    @Test
    void budgetUtilizationAndOverBudgetStateAreAccurate() {
        assertEquals(new BigDecimal("80.00"), service.calculateBudgetUtilization(new BigDecimal("10000.00"), new BigDecimal("8000.00")));
        assertEquals(new BigDecimal("120.00"), service.calculateBudgetUtilization(new BigDecimal("10000.00"), new BigDecimal("12000.00")));
        assertEquals(new BigDecimal("2000.00"), service.calculateBudgetRemaining(new BigDecimal("10000.00"), new BigDecimal("8000.00")));
        assertEquals(new BigDecimal("-2000.00"), service.calculateBudgetRemaining(new BigDecimal("10000.00"), new BigDecimal("12000.00")));
        assertEquals("OVER_BUDGET", service.evaluateBudgetStatus(new BigDecimal("10000.00"), new BigDecimal("12000.00")));
    }

    @Test
    void accountBalanceFollowsOpeningBalanceAndTransactions() {
        BigDecimal balance = service.calculateAccountBalance(
                new BigDecimal("1500.00"),
                new BigDecimal("800.00"),
                new BigDecimal("300.00"),
                new BigDecimal("150.00")
        );
        assertEquals(new BigDecimal("2150.00"), balance);
        assertNotNull(balance);
    }
}
