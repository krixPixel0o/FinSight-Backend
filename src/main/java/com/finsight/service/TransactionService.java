package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.TransactionRequest;
import com.finsight.dto.TransactionResponse;
import com.finsight.entity.Account;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.CategoryRepository;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository,
                             AccountRepository accountRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public TransactionResponse create(String email, TransactionRequest request) {
        User user = findUser(email);
        validate(request, user);

        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setType(request.type());
        if (request.type() == TransactionType.TRANSFER) {
            Account source = resolveOwnedAccount(user, request.sourceAccountId());
            Account target = resolveOwnedAccount(user, request.targetAccountId());
            if (source.getId().equals(target.getId())) {
                throw new IllegalArgumentException("Transfer source and destination cannot be the same");
            }
            transaction.setSourceAccount(source);
            transaction.setTargetAccount(target);
            transaction.setAccount(source);
        } else {
            transaction.setAccount(resolveOwnedAccount(user, request.accountId()));
            transaction.setSourceAccount(null);
            transaction.setTargetAccount(null);
        }

        copy(request, transaction, user);
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAll(String email, TransactionType type, String category,
                                             BigDecimal minAmount, BigDecimal maxAmount,
                                             LocalDate from, LocalDate to, Long accountId, String search, Pageable pageable) {
        User user = findUser(email);
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Minimum amount cannot exceed maximum amount");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        Specification<FinanceTransaction> specification = (root, query, builder) ->
                builder.equal(root.get("user"), user);
        if (type != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("type"), type));
        if (category != null && !category.isBlank()) specification = specification.and((root, query, builder) ->
                builder.equal(builder.lower(root.get("category")), category.toLowerCase()));
        if (minAmount != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("amount"), minAmount));
        if (maxAmount != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("amount"), maxAmount));
        if (from != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("transactionDate"), from));
        if (to != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("transactionDate"), to));
        if (accountId != null) {
            resolveOwnedAccount(user, accountId);
            specification = specification.and((root, query, builder) -> {
                var account = root.join("account", jakarta.persistence.criteria.JoinType.LEFT);
                var source = root.join("sourceAccount", jakarta.persistence.criteria.JoinType.LEFT);
                var target = root.join("targetAccount", jakarta.persistence.criteria.JoinType.LEFT);
                return builder.or(builder.equal(account.get("id"), accountId),
                        builder.equal(source.get("id"), accountId), builder.equal(target.get("id"), accountId));
            });
        }
        if (search != null && !search.isBlank()) {
            String value = "%" + search.toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("description")), value),
                    builder.like(builder.lower(root.get("category")), value),
                    builder.like(builder.lower(root.get("notes")), value)));
        }
        return transactionRepository.findAll(specification, pageable).map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(String email, Long id) {
        return TransactionResponse.from(ownedTransaction(email, id));
    }

    @Transactional
    public TransactionResponse update(String email, Long id, TransactionRequest request) {
        User user = findUser(email);
        validate(request, user);
        FinanceTransaction transaction = ownedTransaction(email, id);
        copy(request, transaction, user);
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(String email, Long id) {
        transactionRepository.delete(ownedTransaction(email, id));
    }

    private FinanceTransaction ownedTransaction(String email, Long id) {
        User user = findUser(email);
        return transactionRepository.findById(id)
                .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void validate(TransactionRequest request, User user) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        if (request.category() == null || request.category().isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
        if (request.type() != TransactionType.TRANSFER
                && !categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(user.getId(), request.category().trim(), request.type())) {
            throw new IllegalArgumentException("Choose one of your categories for this transaction type");
        }
        if (request.transactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }
        if (request.currency() != null && !request.currency().matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency must be a valid ISO code");
        }
        if (request.type() == TransactionType.TRANSFER) {
            if (request.sourceAccountId() == null || request.targetAccountId() == null) {
                throw new IllegalArgumentException("Transfer requires both source and destination accounts");
            }
            if (request.sourceAccountId().equals(request.targetAccountId())) {
                throw new IllegalArgumentException("Transfer source and destination cannot be the same");
            }
        } else if (request.accountId() == null && request.sourceAccountId() == null && request.targetAccountId() == null) {
            resolveDefaultAccount(user);
        }
    }

    private Account resolveOwnedAccount(User user, Long accountId) {
        if (accountId == null) {
            return resolveDefaultAccount(user);
        }
        return accountRepository.findById(accountId)
                .filter(account -> account.getUser().getId().equals(user.getId()) && account.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private Account resolveDefaultAccount(User user) {
        return accountRepository.findFirstByUserIdAndActiveTrueOrderByNameAsc(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active account found for this user"));
    }

    private void copy(TransactionRequest request, FinanceTransaction transaction, User user) {
        transaction.setType(request.type());
        if (request.type() == TransactionType.TRANSFER) {
            Account source = resolveOwnedAccount(user, request.sourceAccountId());
            Account target = resolveOwnedAccount(user, request.targetAccountId());
            if (source.getId().equals(target.getId())) {
                throw new IllegalArgumentException("Transfer source and destination cannot be the same");
            }
            if (!source.getCurrency().equalsIgnoreCase(target.getCurrency())) {
                throw new IllegalArgumentException("Transfers between different currencies are not supported");
            }
            validateCurrency(request.currency(), source.getCurrency());
            transaction.setAccount(source);
            transaction.setSourceAccount(source);
            transaction.setTargetAccount(target);
        } else {
            Account account = resolveOwnedAccount(user, request.accountId());
            validateCurrency(request.currency(), account.getCurrency());
            transaction.setAccount(account);
            transaction.setSourceAccount(null);
            transaction.setTargetAccount(null);
        }
        transaction.setAmount(request.amount());
        transaction.setCategory(request.category().trim());
        transaction.setDescription(request.description().trim());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setNotes(request.notes() == null ? null : request.notes().trim());
        transaction.setCurrency(request.currency() == null
                ? (request.type() == TransactionType.TRANSFER ? transaction.getSourceAccount().getCurrency() : transaction.getAccount().getCurrency())
                : request.currency().trim().toUpperCase());
    }

    private void validateCurrency(String requestedCurrency, String accountCurrency) {
        if (requestedCurrency != null && !requestedCurrency.trim().equalsIgnoreCase(accountCurrency)) {
            throw new IllegalArgumentException("Transaction currency must match the account currency (" + accountCurrency + ")");
        }
    }
}
