package com.finsight.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.finsight.entity.FinancialGoal;

public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    List<FinancialGoal> findByUserIdOrderByTargetDateAsc(Long userId);
    List<FinancialGoal> findByUserIdAndActiveTrue(Long userId);
    long countByUserId(Long userId);
}
