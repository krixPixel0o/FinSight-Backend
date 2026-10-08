package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.AccountRequest;
import com.finsight.dto.AccountResponse;
import com.finsight.entity.Account;
import com.finsight.entity.User;
import com.finsight.exception.ConflictException;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AccountBalanceService accountBalanceService;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          TransactionRepository transactionRepository, AccountBalanceService accountBalanceService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.accountBalanceService = accountBalanceService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll(String email) {
        User user = findUser(email);
        List<Account> accounts = accountRepository.findByUserOrderByName(user);
        Map<Long, BigDecimal> balances = accountBalanceService.balances(accounts, LocalDate.now());
        return accounts.stream()
                .map(account -> response(account, balances.getOrDefault(account.getId(), account.getOpeningBalance())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(String email, Long id) {
        User user = findUser(email);
        Account account = ownedAccount(user, id);
        BigDecimal balance = accountBalanceService.balances(List.of(account), LocalDate.now())
                .getOrDefault(account.getId(), account.getOpeningBalance());
        return response(account, balance);
    }

    @Transactional
    public AccountResponse create(String email, AccountRequest request) {
        User user = findUser(email);
        validateRequest(request, user);
        String normalizedName = request.name().trim();
        if (accountRepository.existsByUserAndNameIgnoreCase(user, normalizedName)) {
            throw new ConflictException("Account name already exists");
        }

        Account account = new Account();
        account.setUser(user);
        account.setName(normalizedName);
        account.setType(request.type());
        account.setCurrency(request.currency() == null ? user.getCurrency() : request.currency().trim().toUpperCase());
        account.setOpeningBalance(request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance());
        account.setActive(request.active());

        Account saved = accountRepository.save(account);
        return response(saved, saved.getOpeningBalance());
    }

    @Transactional
    public AccountResponse update(String email, Long id, AccountRequest request) {
        User user = findUser(email);
        validateRequest(request, user);
        Account account = ownedAccount(user, id);
        boolean hasTransactions = transactionRepository.existsByUserAndAccount(user.getId(), account.getId());
        BigDecimal requestedOpeningBalance = request.openingBalance() == null
                ? BigDecimal.ZERO : request.openingBalance();
        String requestedCurrency = request.currency() == null
                ? user.getCurrency() : request.currency().trim().toUpperCase();
        if (hasTransactions && account.getOpeningBalance().compareTo(requestedOpeningBalance) != 0) {
            throw new ConflictException("Opening balance cannot change after transactions have been recorded");
        }
        if (hasTransactions && !account.getCurrency().equalsIgnoreCase(requestedCurrency)) {
            throw new ConflictException("Account currency cannot change after transactions have been recorded");
        }
        String normalizedName = request.name().trim();
        if (!account.getName().equalsIgnoreCase(normalizedName)
                && accountRepository.existsByUserAndNameIgnoreCase(user, normalizedName)) {
            throw new ConflictException("Account name already exists");
        }

        account.setName(normalizedName);
        account.setType(request.type());
        account.setCurrency(request.currency() == null ? user.getCurrency() : request.currency().trim().toUpperCase());
        account.setOpeningBalance(request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance());
        account.setActive(request.active());

        Account saved = accountRepository.save(account);
        BigDecimal balance = accountBalanceService.balances(List.of(saved), LocalDate.now())
                .getOrDefault(saved.getId(), saved.getOpeningBalance());
        return response(saved, balance);
    }

    @Transactional
    public void delete(String email, Long id) {
        User user = findUser(email);
        Account account = ownedAccount(user, id);
        if (transactionRepository.existsByUserAndAccount(user.getId(), account.getId())) {
            throw new ConflictException("Cannot delete account because it has linked transactions");
        }
        accountRepository.delete(account);
    }

    @Transactional
    public Account getOrCreateDefaultAccount(User user) {
        return accountRepository.findFirstByUserIdAndActiveTrueOrderByNameAsc(user.getId())
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setUser(user);
                    account.setName("Cash");
                    account.setType(com.finsight.entity.AccountType.CASH);
                    account.setCurrency(user.getCurrency());
                    account.setOpeningBalance(BigDecimal.ZERO);
                    account.setActive(true);
                    return accountRepository.save(account);
                });
    }

    private void validateRequest(AccountRequest request, User user) {
        if (request == null) {
            throw new IllegalArgumentException("Account details are required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Account name is required");
        }
        if (request.name().trim().length() < 2 || request.name().trim().length() > 80) {
            throw new IllegalArgumentException("Account name must be between 2 and 80 characters");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("Account type is required");
        }
        if (request.openingBalance() != null && request.openingBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative");
        }
        if (request.currency() != null && !request.currency().matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency must be a valid ISO code");
        }
        String currency = request.currency() == null ? user.getCurrency() : request.currency().trim().toUpperCase();
        if (!currency.equalsIgnoreCase(user.getCurrency())) {
            throw new IllegalArgumentException("Account currency must match your preferred currency (" + user.getCurrency() + ")");
        }
    }

    private Account ownedAccount(User user, Long id) {
        return accountRepository.findById(id)
                .filter(account -> account.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AccountResponse response(Account account, BigDecimal balance) {
        return AccountResponse.from(account, balance);
    }
}
