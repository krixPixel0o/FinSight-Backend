package com.finsight.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "liabilities")
public class Liability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private LiabilityType type;
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2) private BigDecimal currentBalance;
    @Column(name = "annual_interest_rate", nullable = false, precision = 7, scale = 4) private BigDecimal annualInterestRate;
    @Column(name = "monthly_payment", nullable = false, precision = 19, scale = 2) private BigDecimal monthlyPayment;
    @Column(length = 3, nullable = false) private String currency;
    @Column(name = "as_of_date", nullable = false) private LocalDate asOfDate;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LiabilityType getType() { return type; }
    public void setType(LiabilityType value) { type = value; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal value) { currentBalance = value; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public void setAnnualInterestRate(BigDecimal value) { annualInterestRate = value; }
    public BigDecimal getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(BigDecimal value) { monthlyPayment = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate value) { asOfDate = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
