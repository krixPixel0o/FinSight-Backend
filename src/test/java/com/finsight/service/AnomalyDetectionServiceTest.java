package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnomalyDetectionServiceTest {

    @Autowired private AnomalyDetectionService service;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void flagsLargeExpenseAgainstCategoryBaselineAndIsolatesUsers() {
        User user = userRepository.save(new User("Riley", "anomaly@example.com", "encoded"));
        User otherUser = userRepository.save(new User("Jamie", "other-anomaly@example.com", "encoded"));
        for (int i = 0; i < 5; i++) {
            expense(user, "Food", new BigDecimal("100.00").add(BigDecimal.valueOf(i * 2)), LocalDate.now().minusDays(60 + i * 10L));
            expense(otherUser, "Food", new BigDecimal("100.00").add(BigDecimal.valueOf(i * 2)), LocalDate.now().minusDays(60 + i * 10L));
        }
        FinanceTransaction ownOutlier = expense(user, "Food", new BigDecimal("500.00"), LocalDate.now().minusDays(1));
        expense(otherUser, "Food", new BigDecimal("9000.00"), LocalDate.now().minusDays(1));

        var result = service.detect(user.getEmail());

        assertEquals("ANALYZED", result.status());
        assertEquals(1, result.anomalies().size());
        assertEquals(ownOutlier.getId(), result.anomalies().get(0).transactionId());
        assertEquals(5, result.anomalies().get(0).baselineTransactions());
        assertEquals(new BigDecimal("500.00"), result.anomalies().get(0).amount());
        assertFalse(result.anomalies().get(0).explanation().isBlank());
    }

    @Test
    void reportsInsufficientHistoryInsteadOfClaimingNoAnomalies() {
        User user = userRepository.save(new User("Sam", "new-anomaly-user@example.com", "encoded"));

        var result = service.detect(user.getEmail());

        assertEquals("INSUFFICIENT_HISTORY", result.status());
        assertEquals(0, result.anomalies().size());
    }

    private FinanceTransaction expense(User user, String category, BigDecimal amount, LocalDate date) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setDescription(category + " purchase");
        transaction.setCurrency("INR");
        transaction.setTransactionDate(date);
        return transactionRepository.save(transaction);
    }
}
