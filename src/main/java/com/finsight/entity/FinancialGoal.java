package com.finsight.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "financial_goals")
public class FinancialGoal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2) private BigDecimal targetAmount;
    @Column(name = "current_amount", nullable = false, precision = 19, scale = 2) private BigDecimal currentAmount;
    @Column(name = "planned_monthly_contribution", nullable = false, precision = 19, scale = 2) private BigDecimal plannedMonthlyContribution;
    @Column(length = 3, nullable = false) private String currency;
    @Column(name = "target_date") private LocalDate targetDate;
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
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal value) { targetAmount = value; }
    public BigDecimal getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(BigDecimal value) { currentAmount = value; }
    public BigDecimal getPlannedMonthlyContribution() { return plannedMonthlyContribution; }
    public void setPlannedMonthlyContribution(BigDecimal value) { plannedMonthlyContribution = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate value) { targetDate = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
