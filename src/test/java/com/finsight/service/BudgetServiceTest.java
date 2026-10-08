package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.BudgetRequest;
import com.finsight.entity.BudgetPeriod;
import com.finsight.entity.Category;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BudgetServiceTest {

    @Autowired private BudgetService budgetService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void futureTransactionsDoNotCountAsBudgetSpend() {
        User user = userRepository.save(new User("Alice", "alice@example.com", passwordEncoder.encode("secret123")));
        Category category = new Category();
        category.setUser(user);
        category.setName("Food");
        category.setType(TransactionType.EXPENSE);
        categoryRepository.save(category);

        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(10);
        FinanceTransaction futureExpense = new FinanceTransaction();
        futureExpense.setUser(user);
        futureExpense.setType(TransactionType.EXPENSE);
        futureExpense.setAmount(new BigDecimal("50.00"));
        futureExpense.setCategory("Food");
        futureExpense.setDescription("Scheduled expense");
        futureExpense.setCurrency("INR");
        futureExpense.setTransactionDate(futureDate);
        transactionRepository.save(futureExpense);

        var budget = budgetService.create("alice@example.com", new BudgetRequest(
                "Food", new BigDecimal("500.00"), BudgetPeriod.CUSTOM, today, futureDate));

        assertEquals(BigDecimal.ZERO, budget.spent());
    }
}
