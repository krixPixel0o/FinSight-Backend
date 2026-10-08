package com.finsight.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finsight.dto.AccountRequest;
import com.finsight.dto.AccountResponse;
import com.finsight.service.AccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/API/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> findAll(Authentication authentication) {
        return accountService.findAll(authentication.getName());
    }

    @GetMapping("/{id}")
    public AccountResponse findById(Authentication authentication, @PathVariable Long id) {
        return accountService.findById(authentication.getName(), id);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(Authentication authentication, @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public AccountResponse update(Authentication authentication, @PathVariable Long id,
                                 @Valid @RequestBody AccountRequest request) {
        return accountService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        accountService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
