package com.finsight.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finsight.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
