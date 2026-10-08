package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.ReportSummaryResponse;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@Service
public class ReportService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final FinancialMathService financialMathService;

    public ReportService(TransactionRepository transactionRepository, UserRepository userRepository,
                         FinancialMathService financialMathService) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.financialMathService = financialMathService;
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary(String email, LocalDate requestedFrom, LocalDate requestedTo) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.withDayOfMonth(1).minusMonths(11) : requestedFrom;
        if (from.isAfter(to)) throw new IllegalArgumentException("Start date cannot be after end date");

        BigDecimal income = transactionRepository.sumByTypeBetween(user.getId(), TransactionType.INCOME, from, to);
        BigDecimal expenses = transactionRepository.sumByTypeBetween(user.getId(), TransactionType.EXPENSE, from, to);
        List<Object[]> categoryRows = transactionRepository.sumByCategoryBetween(user.getId(), TransactionType.EXPENSE, from, to);
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        categoryRows.forEach(row -> categories.put((String) row[0], (BigDecimal) row[1]));

        Map<String, BigDecimal> monthly = new LinkedHashMap<>();
        YearMonth month = YearMonth.from(from);
        YearMonth lastMonth = YearMonth.from(to);
        while (!month.isAfter(lastMonth)) {
            LocalDate monthFrom = month.atDay(1).isBefore(from) ? from : month.atDay(1);
            LocalDate monthTo = month.atEndOfMonth().isAfter(to) ? to : month.atEndOfMonth();
            BigDecimal amount = transactionRepository.sumByTypeBetween(user.getId(), TransactionType.EXPENSE, monthFrom, monthTo);
            monthly.put(month.toString(), amount == null ? BigDecimal.ZERO : amount);
            month = month.plusMonths(1);
        }

        long count = transactionRepository.countByUserIdAndTransactionDateBetween(user.getId(), from, to);
        return new ReportSummaryResponse(from, to, safe(income), safe(expenses),
                financialMathService.calculateNetCashFlow(income, expenses),
                financialMathService.calculateSavingsRate(income, expenses), count, categories, monthly);
    }

    @Transactional(readOnly = true)
    public String transactionsCsv(String email, LocalDate from, LocalDate to) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? LocalDate.of(1900, 1, 1) : from;
        if (start.isAfter(end)) throw new IllegalArgumentException("Start date cannot be after end date");
        List<FinanceTransaction> transactions = transactionRepository
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(user.getId(), start, end);
        StringBuilder csv = new StringBuilder("date,type,category,description,account,amount,notes\n");
        transactions.forEach(transaction -> csv.append(row(transaction.getTransactionDate().toString()))
                .append(',').append(row(transaction.getType().name())).append(',').append(row(transaction.getCategory()))
                .append(',').append(row(transaction.getDescription())).append(',')
                .append(row(transaction.getAccount() == null ? "" : transaction.getAccount().getName())).append(',')
                .append(transaction.getAmount()).append(',').append(row(transaction.getNotes())).append('\n'));
        return csv.toString();
    }

    private String row(String value) { return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\""; }
    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
