package com.finsight.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.AccountRequest;
import com.finsight.dto.LoginRequest;
import com.finsight.dto.RegisterRequest;
import com.finsight.entity.AccountType;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.exception.ConflictException;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccessControlServiceTest {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void validLoginGeneratesToken() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("secret123");
        authService.registerUser(request);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("  ALICE@EXAMPLE.COM  ");
        loginRequest.setPwd("secret123");

        String token = authService.loginUser(loginRequest);

        assertTrue(token.contains("."));
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void registrationTreatsEmailAddressesAsCaseInsensitive() {
        RegisterRequest first = new RegisterRequest();
        first.setName("Alice");
        first.setEmail("Alice@Example.com");
        first.setPassword("secret123");
        authService.registerUser(first);

        RegisterRequest duplicate = new RegisterRequest();
        duplicate.setName("Another Alice");
        duplicate.setEmail("alice@example.com");
        duplicate.setPassword("secret123");

        assertThrows(ConflictException.class, () -> authService.registerUser(duplicate));
    }

    @Test
    void invalidLoginThrowsError() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("missing@example.com");
        loginRequest.setPwd("wrongpass");

        assertThrows(IllegalArgumentException.class, () -> authService.loginUser(loginRequest));
    }

    @Test
    void userCannotAccessAnotherUsersAccount() {
        User userA = new User("Alice", "alice@example.com", passwordEncoder.encode("secret123"));
        User userB = new User("Bob", "bob@example.com", passwordEncoder.encode("secret123"));
        userRepository.save(userA);
        userRepository.save(userB);

        AccountRequest request = new AccountRequest("Shared", AccountType.BANK, "INR", new BigDecimal("2500.00"), true);
        var accountA = accountService.create("alice@example.com", request);

        AccountRequest otherUsersRequest = new AccountRequest("Other", AccountType.CASH, "INR", new BigDecimal("500.00"), true);
        var accountB = accountService.create("bob@example.com", otherUsersRequest);

        assertEquals(1, accountRepository.findByUserOrderByName(userA).size());
        assertEquals(1, accountRepository.findByUserOrderByName(userB).size());
        assertThrows(ResourceNotFoundException.class,
                () -> accountService.findById("bob@example.com", accountA.id()));
        assertThrows(ResourceNotFoundException.class,
                () -> accountService.update("alice@example.com", accountB.id(), request));
        assertThrows(ResourceNotFoundException.class,
                () -> accountService.delete("alice@example.com", accountB.id()));
    }

    @Test
    void accountOpeningBalanceCannotBeRewrittenAfterTransactionsExist() {
        User user = new User("Alice", "alice@example.com", passwordEncoder.encode("secret123"));
        userRepository.save(user);
        var account = accountService.create("alice@example.com",
                new AccountRequest("Checking", AccountType.BANK, "INR", new BigDecimal("1000.00"), true));

        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setAccount(accountRepository.findById(account.id()).orElseThrow());
        transaction.setType(TransactionType.INCOME);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCategory("Salary");
        transaction.setDescription("Pay");
        transaction.setCurrency("INR");
        transaction.setTransactionDate(LocalDate.now());
        transactionRepository.save(transaction);

        AccountRequest rewriteOpeningBalance = new AccountRequest("Checking", AccountType.BANK,
                "INR", new BigDecimal("5000.00"), true);
        assertThrows(ConflictException.class,
                () -> accountService.update("alice@example.com", account.id(), rewriteOpeningBalance));
    }

    @Test
    void accountCurrencyDefaultsToUsersPreferredCurrencyOnCreateAndUpdate() {
        User user = new User("Taylor", "currency-owner@example.com", passwordEncoder.encode("secret123"));
        user.setCurrency("USD");
        userRepository.save(user);

        AccountRequest omittedCurrency = new AccountRequest("Daily", AccountType.BANK, null, BigDecimal.ZERO, true);
        var created = accountService.create(user.getEmail(), omittedCurrency);
        assertEquals("USD", created.currency());

        var updated = accountService.update(user.getEmail(), created.id(), omittedCurrency);
        assertEquals("USD", updated.currency());
    }
}
