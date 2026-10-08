package com.finsight.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finsight.entity.Account;
import com.finsight.entity.AccountType;
import com.finsight.entity.User;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserOrderByName(User user);
    List<Account> findByUserAndActiveTrueOrderByName(User user);
    Optional<Account> findByUserAndNameIgnoreCaseAndActiveTrue(User user, String name);
    boolean existsByUserAndNameIgnoreCase(User user, String name);
    List<Account> findByUserIdAndActiveTrueOrderByName(Long userId);
    Optional<Account> findFirstByUserIdAndActiveTrueOrderByNameAsc(Long userId);
    long countByUserIdAndType(Long userId, AccountType type);
    long countByUserIdAndCurrencyNot(Long userId, String currency);
}
