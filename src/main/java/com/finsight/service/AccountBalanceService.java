package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.entity.Account;
import com.finsight.entity.TransactionType;
import com.finsight.repository.TransactionRepository;

/** Computes balances from opening balances and posted activity, without loading transaction histories. */
@Service
public class AccountBalanceService {

    private final TransactionRepository transactionRepository;

    public AccountBalanceService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> balances(List<Account> accounts, LocalDate asOfDate) {
        if (accounts.isEmpty()) return Map.of();

        Long userId = accounts.get(0).getUser().getId();
        Map<Long, BigDecimal> result = new HashMap<>();
        accounts.forEach(account -> result.put(account.getId(), safe(account.getOpeningBalance())));

        apply(result, transactionRepository.sumByAccountAndTypeToDate(userId, TransactionType.INCOME, asOfDate), false);
        apply(result, transactionRepository.sumByAccountAndTypeToDate(userId, TransactionType.EXPENSE, asOfDate), true);
        apply(result, transactionRepository.sumOutgoingTransfersToDate(userId, asOfDate), true);
        apply(result, transactionRepository.sumIncomingTransfersToDate(userId, asOfDate), false);
        return Map.copyOf(result);
    }

    private void apply(Map<Long, BigDecimal> balances, List<Object[]> groupedAmounts, boolean subtract) {
        for (Object[] row : groupedAmounts) {
            Long accountId = (Long) row[0];
            BigDecimal amount = safe((BigDecimal) row[1]);
            if (!balances.containsKey(accountId)) continue;
            balances.compute(accountId, (id, current) -> subtract ? current.subtract(amount) : current.add(amount));
        }
    }

    private BigDecimal safe(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
