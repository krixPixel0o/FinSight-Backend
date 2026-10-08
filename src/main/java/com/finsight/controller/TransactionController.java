package com.finsight.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finsight.dto.TransactionRequest;
import com.finsight.dto.TransactionResponse;
import com.finsight.entity.TransactionType;
import com.finsight.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/API/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(Authentication authentication,
                                                       @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(authentication.getName(), request));
    }

    @GetMapping
    public Page<TransactionResponse> findAll(Authentication authentication,
                                             @RequestParam(required = false) TransactionType type,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) BigDecimal minAmount,
                                             @RequestParam(required = false) BigDecimal maxAmount,
                                             @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long accountId,
                                             @RequestParam(required = false) String search,
                                             @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.findAll(authentication.getName(), type, category, minAmount, maxAmount,
                from, to, accountId, search, pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponse findById(Authentication authentication, @PathVariable Long id) {
        return transactionService.findById(authentication.getName(), id);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(Authentication authentication, @PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        transactionService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
