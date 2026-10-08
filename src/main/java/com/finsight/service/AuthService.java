package com.finsight.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.finsight.dto.ChangePasswordRequest;
import com.finsight.dto.LoginRequest;
import com.finsight.dto.RegisterRequest;
import com.finsight.dto.UserProfileRequest;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.UserRepository;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.exception.ConflictException;
import com.finsight.security.JwtUtil;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AccountRepository accountRepository,
                       TransactionRepository transactionRepository) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public User registerUser(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        String hashedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName().trim(),
                email,
                hashedPassword
        );

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public String loginUser(LoginRequest request) {

        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(
                request.getPwd(),
                user.getPassword())) {

            throw new IllegalArgumentException(
                    "Invalid email or password");
        }

        return jwtUtil.generateToken(user.getEmail());
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public User updateProfile(String email, UserProfileRequest request) {
        User user = getUserByEmail(email);
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        user.setName(request.name().trim());
        if (request.currency() != null && !request.currency().isBlank()) {
            String currency = request.currency().trim().toUpperCase();
            if (!currency.equalsIgnoreCase(user.getCurrency())
                    && (!accountRepository.findByUserOrderByName(user).isEmpty()
                    || transactionRepository.countByUserId(user.getId()) > 0)) {
                throw new ConflictException("Currency cannot change after financial records exist; conversion is not supported");
            }
            user.setCurrency(currency);
        }
        return userRepository.save(user);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = getUserByEmail(email);
        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
