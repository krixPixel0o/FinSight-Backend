package com.finsight.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.finsight.entity.Liability;

public interface LiabilityRepository extends JpaRepository<Liability, Long> {
    List<Liability> findByUserIdOrderByNameAsc(Long userId);
    List<Liability> findByUserIdAndActiveTrue(Long userId);
    long countByUserId(Long userId);
}
