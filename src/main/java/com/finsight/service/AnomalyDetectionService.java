package com.finsight.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.AnomalyResponse;
import com.finsight.dto.AnomalyResponse.Anomaly;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

/** Flags unusually large category expenses using only the user's prior expense history. */
@Service
public class AnomalyDetectionService {

    private static final MathContext CALCULATION = MathContext.DECIMAL64;
    private static final int HISTORY_LIMIT = 5000;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final int minimumBaselineTransactions;
    private final BigDecimal minimumAmountMultiple;
    private final BigDecimal minimumStandardDeviations;

    public AnomalyDetectionService(UserRepository userRepository,
            TransactionRepository transactionRepository,
            @Value("${finsight.anomaly.minimum-baseline-transactions:5}") int minimumBaselineTransactions,
            @Value("${finsight.anomaly.minimum-amount-multiple:2}") BigDecimal minimumAmountMultiple,
            @Value("${finsight.anomaly.minimum-standard-deviations:2}") BigDecimal minimumStandardDeviations) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.minimumBaselineTransactions = minimumBaselineTransactions;
        this.minimumAmountMultiple = minimumAmountMultiple;
        this.minimumStandardDeviations = minimumStandardDeviations;
        if (minimumBaselineTransactions < 2 || minimumAmountMultiple.compareTo(BigDecimal.ONE) <= 0
                || minimumStandardDeviations.signum() <= 0) {
            throw new IllegalArgumentException("Anomaly detection configuration is invalid");
        }
    }

    @Transactional(readOnly = true)
    public AnomalyResponse detect(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate today = LocalDate.now();
        LocalDate recentStart = today.minusDays(29);
        LocalDate baselineEnd = recentStart.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(179);
        Slice<FinanceTransaction> historyPage = transactionRepository
                .findByUserIdAndTypeAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                        user.getId(), TransactionType.EXPENSE, baselineStart, today,
                        PageRequest.of(0, HISTORY_LIMIT));
        List<FinanceTransaction> history = historyPage.getContent();
        Map<String, List<BigDecimal>> baselines = new HashMap<>();
        for (FinanceTransaction transaction : history) {
            if (transaction.getTransactionDate().isBefore(recentStart)) {
                baselines.computeIfAbsent(key(transaction), ignored -> new ArrayList<>())
                        .add(transaction.getAmount());
            }
        }

        List<Anomaly> anomalies = new ArrayList<>();
        for (FinanceTransaction transaction : history) {
            if (transaction.getTransactionDate().isBefore(recentStart)) continue;
            List<BigDecimal> amounts = baselines.getOrDefault(key(transaction), List.of());
            if (amounts.size() < minimumBaselineTransactions) continue;
            Statistics stats = statistics(amounts);
            BigDecimal delta = transaction.getAmount().subtract(stats.average());
            BigDecimal amountFloor = stats.average().multiply(minimumAmountMultiple);
            BigDecimal deviationFloor = stats.standardDeviation().multiply(minimumStandardDeviations);
            if (transaction.getAmount().compareTo(amountFloor) < 0 || delta.compareTo(deviationFloor) < 0) continue;

            BigDecimal zScore = stats.standardDeviation().signum() == 0
                    ? BigDecimal.valueOf(10)
                    : delta.divide(stats.standardDeviation(), CALCULATION);
            int signal = zScore.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(25))
                    .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            anomalies.add(new Anomaly(transaction.getId(), transaction.getTransactionDate(),
                    transaction.getCategory(), transaction.getDescription(), transaction.getAmount(),
                    transaction.getCurrency(), amounts.size(), stats.average(), stats.standardDeviation(),
                    delta, signal, "This expense is at least " + minimumAmountMultiple.toPlainString()
                            + " times the prior category average and exceeds that average by at least "
                            + minimumStandardDeviations.toPlainString() + " standard deviations."));
        }

        anomalies.sort((left, right) -> right.signalStrength() - left.signalStrength());
        boolean usableBaselineExists = baselines.values().stream()
                .anyMatch(amounts -> amounts.size() >= minimumBaselineTransactions);
        String status = historyPage.hasNext() ? "PARTIAL_HISTORY"
                : !usableBaselineExists ? "INSUFFICIENT_HISTORY" : "ANALYZED";
        return new AnomalyResponse(status, recentStart, today, baselineStart, baselineEnd,
                minimumBaselineTransactions, historyPage.hasNext(), List.copyOf(anomalies));
    }

    private Statistics statistics(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        BigDecimal squaredDifferences = values.stream()
                .map(value -> value.subtract(average).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = squaredDifferences.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        BigDecimal deviation = variance.sqrt(CALCULATION);
        return new Statistics(average, deviation);
    }

    private String key(FinanceTransaction transaction) {
        return transaction.getCategory().trim().toLowerCase(Locale.ROOT) + "|" + transaction.getCurrency();
    }

    private record Statistics(BigDecimal average, BigDecimal standardDeviation) { }
}
