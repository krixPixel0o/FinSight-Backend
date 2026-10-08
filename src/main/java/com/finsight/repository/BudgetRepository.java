package com.finsight.repository;

import com.finsight.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserIdOrderByEndDateAsc(Long userId);
    boolean existsByUserIdAndCategoryIgnoreCase(Long userId, String category);
}
