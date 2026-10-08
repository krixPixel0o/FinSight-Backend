package com.finsight.repository;

import com.finsight.entity.Category;
import com.finsight.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIdOrderByName(Long userId);
    boolean existsByUserIdAndNameIgnoreCaseAndType(Long userId, String name, TransactionType type);
}