package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.FinancialGoalRequest;
import com.finsight.dto.LiabilityRequest;
import com.finsight.entity.LiabilityType;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanningServiceTest {
    @Autowired private PlanningService service;
    @Autowired private UserRepository users;

    @Test
    void managesOwnedGoalsAndRejectsCrossUserUpdates() {
        User owner = users.save(new User("Goal Owner", "goal-owner@example.com", "encoded"));
        User other = users.save(new User("Other", "goal-other@example.com", "encoded"));
        var goal = service.createGoal(owner.getEmail(), new FinancialGoalRequest("Emergency fund",
                new BigDecimal("10000.00"), new BigDecimal("2500.00"), new BigDecimal("500.00"), LocalDate.now().plusMonths(6), true));

        assertEquals(new BigDecimal("25.00"), goal.progressPercent());
        assertEquals(new BigDecimal("500.00"), goal.plannedMonthlyContribution());
        assertEquals("IN_PROGRESS", goal.scheduleStatus());
        assertEquals(1, service.goals(owner.getEmail()).size());
        assertEquals(0, service.goals(other.getEmail()).size());
        assertThrows(ResourceNotFoundException.class, () -> service.updateGoal(other.getEmail(), goal.id(),
                new FinancialGoalRequest("Injected", new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, null, true)));
    }

    @Test
    void modelsLiabilityPayoffWithExplicitPaymentCoverage() {
        User owner = users.save(new User("Debt Owner", "debt-owner@example.com", "encoded"));
        var liability = service.createLiability(owner.getEmail(), new LiabilityRequest("Card", LiabilityType.CREDIT_CARD,
                new BigDecimal("1000.00"), new BigDecimal("0.0000"), new BigDecimal("250.00"), LocalDate.now(), true));

        assertEquals(4, liability.estimatedMonthsToPayoff());
        assertEquals("ESTIMATED", liability.payoffStatus());
        assertEquals("INR", liability.currency());

        var uncovered = service.createLiability(owner.getEmail(), new LiabilityRequest("High APR loan", LiabilityType.PERSONAL_LOAN,
                new BigDecimal("1000.00"), new BigDecimal("36.0000"), new BigDecimal("20.00"), LocalDate.now(), true));
        assertEquals("PAYMENT_DOES_NOT_COVER_INTEREST", uncovered.payoffStatus());
        assertEquals(null, uncovered.estimatedMonthsToPayoff());
    }
}
