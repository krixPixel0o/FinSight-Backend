package com.finsight.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.finsight.entity.Account;
import com.finsight.entity.AccountType;

public record AccountResponse(Long id, String name, AccountType type, String currency,
                             BigDecimal openingBalance, BigDecimal currentBalance, boolean active, Instant createdAt, Instant updatedAt) {
    public static AccountResponse from(Account account, BigDecimal currentBalance) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getOpeningBalance(),
                currentBalance,
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
