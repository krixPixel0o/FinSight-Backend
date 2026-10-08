package com.finsight.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;

public interface TransactionRepository extends JpaRepository<FinanceTransaction, Long>,
        JpaSpecificationExecutor<FinanceTransaction> {

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId "
            + "and t.type = com.finsight.entity.TransactionType.EXPENSE and lower(t.category) = lower(:category) "
            + "and t.transactionDate between :startDate and :endDate")
    BigDecimal sumExpenses(@Param("userId") Long userId, @Param("category") String category,
                           @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = :type and t.type <> com.finsight.entity.TransactionType.TRANSFER")
    BigDecimal sumByType(@Param("userId") Long userId, @Param("type") TransactionType type);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = :type and t.type <> com.finsight.entity.TransactionType.TRANSFER and t.transactionDate <= :asOfDate")
    BigDecimal sumByTypeToDate(@Param("userId") Long userId, @Param("type") TransactionType type,
                               @Param("asOfDate") LocalDate asOfDate);

    @Query("select t.account.id, sum(t.amount) from FinanceTransaction t where t.user.id = :userId and t.account is not null and t.type = :type and t.transactionDate <= :asOfDate group by t.account.id")
    List<Object[]> sumByAccountAndTypeToDate(@Param("userId") Long userId, @Param("type") TransactionType type,
                                             @Param("asOfDate") LocalDate asOfDate);

    @Query("select t.sourceAccount.id, sum(t.amount) from FinanceTransaction t where t.user.id = :userId and t.type = com.finsight.entity.TransactionType.TRANSFER and t.sourceAccount is not null and t.transactionDate <= :asOfDate group by t.sourceAccount.id")
    List<Object[]> sumOutgoingTransfersToDate(@Param("userId") Long userId, @Param("asOfDate") LocalDate asOfDate);

    @Query("select t.targetAccount.id, sum(t.amount) from FinanceTransaction t where t.user.id = :userId and t.type = com.finsight.entity.TransactionType.TRANSFER and t.targetAccount is not null and t.transactionDate <= :asOfDate group by t.targetAccount.id")
    List<Object[]> sumIncomingTransfersToDate(@Param("userId") Long userId, @Param("asOfDate") LocalDate asOfDate);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = :type and t.type <> com.finsight.entity.TransactionType.TRANSFER and t.transactionDate between :startDate and :endDate")
    BigDecimal sumByTypeBetween(@Param("userId") Long userId, @Param("type") TransactionType type,
                                @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select t.category, sum(t.amount) from FinanceTransaction t where t.user.id = :userId and t.type = :type and t.type <> com.finsight.entity.TransactionType.TRANSFER and t.transactionDate between :startDate and :endDate group by t.category order by sum(t.amount) desc")
    List<Object[]> sumByCategoryBetween(@Param("userId") Long userId, @Param("type") TransactionType type,
                                        @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = com.finsight.entity.TransactionType.INCOME and t.transactionDate between :startDate and :endDate")
    BigDecimal sumIncomeBetween(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = com.finsight.entity.TransactionType.EXPENSE and t.transactionDate between :startDate and :endDate")
    BigDecimal sumExpenseBetween(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = com.finsight.entity.TransactionType.TRANSFER and t.sourceAccount.id = :accountId and t.transactionDate between :startDate and :endDate")
    BigDecimal sumOutgoingTransfers(@Param("userId") Long userId, @Param("accountId") Long accountId,
                                   @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select coalesce(sum(t.amount), 0) from FinanceTransaction t where t.user.id = :userId and t.type = com.finsight.entity.TransactionType.TRANSFER and t.targetAccount.id = :accountId and t.transactionDate between :startDate and :endDate")
    BigDecimal sumIncomingTransfers(@Param("userId") Long userId, @Param("accountId") Long accountId,
                                   @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select case when count(t) > 0 then true else false end from FinanceTransaction t where t.user.id = :userId and (t.account.id = :accountId or t.sourceAccount.id = :accountId or t.targetAccount.id = :accountId)")
    boolean existsByUserAndAccount(@Param("userId") Long userId, @Param("accountId") Long accountId);

    boolean existsByUserIdAndCategoryIgnoreCase(Long userId, String category);

    boolean existsByUserIdAndAccountId(Long userId, Long accountId);

    Page<FinanceTransaction> findByUserIdOrderByTransactionDateDescCreatedAtDesc(Long userId, Pageable pageable);

    Page<FinanceTransaction> findByUserIdAndTransactionDateLessThanEqualOrderByTransactionDateDescCreatedAtDesc(
            Long userId, LocalDate asOfDate, Pageable pageable);

    List<FinanceTransaction> findByUserIdOrderByTransactionDateDesc(Long userId);

    List<FinanceTransaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(Long userId, LocalDate from, LocalDate to);

    Slice<FinanceTransaction> findByUserIdAndTypeAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
            Long userId, TransactionType type, LocalDate from, LocalDate to, Pageable pageable);

    long countByUserIdAndTransactionDateBetween(Long userId, LocalDate from, LocalDate to);

    @Query("select count(t) from FinanceTransaction t where t.user.id = :userId and t.type <> com.finsight.entity.TransactionType.TRANSFER and t.transactionDate between :from and :to")
    long countFinancialTransactionsBetween(@Param("userId") Long userId, @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    long countByUserIdAndCurrencyNot(Long userId, String currency);

    long countByUserId(Long userId);
}
