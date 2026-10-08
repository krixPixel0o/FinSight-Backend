package com.finsight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        String category,
        String description,
        LocalDate transactionDate,
        String notes,
        Long accountId,
        String accountName,
        Instant createdAt) {

    public static TransactionResponse from(FinanceTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getType(), transaction.getAmount(),
                transaction.getCategory(), transaction.getDescription(),
                transaction.getTransactionDate(), transaction.getNotes(),
                transaction.getAccount() == null ? null : transaction.getAccount().getId(),
                transaction.getAccount() == null ? null : transaction.getAccount().getName(),
                transaction.getCreatedAt());
    }
}
