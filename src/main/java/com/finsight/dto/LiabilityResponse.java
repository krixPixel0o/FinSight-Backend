package com.finsight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.finsight.entity.Liability;
import com.finsight.entity.LiabilityType;

public record LiabilityResponse(Long id, String name, LiabilityType type, BigDecimal currentBalance,
        BigDecimal annualInterestRate, BigDecimal monthlyPayment, String currency, LocalDate asOfDate,
        boolean active, Integer estimatedMonthsToPayoff, String payoffStatus) {
    public static LiabilityResponse from(Liability liability) {
        if (!liability.isActive() || liability.getCurrentBalance().signum() <= 0) {
            return new LiabilityResponse(liability.getId(), liability.getName(), liability.getType(),
                    liability.getCurrentBalance(), liability.getAnnualInterestRate(), liability.getMonthlyPayment(),
                    liability.getCurrency(), liability.getAsOfDate(), liability.isActive(),
                    liability.getCurrentBalance().signum() <= 0 ? 0 : null,
                    liability.getCurrentBalance().signum() <= 0 ? "PAID" : "INACTIVE");
        }
        BigDecimal monthlyRate = liability.getAnnualInterestRate().divide(BigDecimal.valueOf(1200), 12,
                java.math.RoundingMode.HALF_UP);
        BigDecimal monthlyInterest = liability.getCurrentBalance().multiply(monthlyRate);
        if (liability.getMonthlyPayment().compareTo(monthlyInterest) <= 0) {
            return new LiabilityResponse(liability.getId(), liability.getName(), liability.getType(),
                    liability.getCurrentBalance(), liability.getAnnualInterestRate(), liability.getMonthlyPayment(),
                    liability.getCurrency(), liability.getAsOfDate(), true, null, "PAYMENT_DOES_NOT_COVER_INTEREST");
        }
        BigDecimal balance = liability.getCurrentBalance();
        int months = 0;
        while (balance.signum() > 0 && months < 1200) {
            balance = balance.add(balance.multiply(monthlyRate)).subtract(liability.getMonthlyPayment())
                    .max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
            months++;
        }
        boolean calculated = balance.signum() <= 0;
        return new LiabilityResponse(liability.getId(), liability.getName(), liability.getType(),
                liability.getCurrentBalance(), liability.getAnnualInterestRate(), liability.getMonthlyPayment(),
                liability.getCurrency(), liability.getAsOfDate(), true, calculated ? months : null,
                calculated ? "ESTIMATED" : "BEYOND_100_YEARS");
    }
}
