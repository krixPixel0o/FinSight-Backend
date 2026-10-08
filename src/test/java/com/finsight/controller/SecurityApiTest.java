package com.finsight.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.finsight.entity.Budget;
import com.finsight.entity.BudgetPeriod;
import com.finsight.entity.FinanceTransaction;
import com.finsight.entity.TransactionType;
import com.finsight.repository.BudgetRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void protectedIntelligenceEndpointRequiresJwt() throws Exception {
        mockMvc.perform(get("/API/intelligence/health-score"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/API/goals")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/API/liabilities")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/API/intelligence/recommendations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/API/intelligence/assistant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recommendationsAreScopedToTheAuthenticatedUserAndReturnTheExpectedShape() throws Exception {
        String ownerEmail = "recommendation-owner@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        String otherToken = registerAndLogin("recommendation-other@example.com");
        var owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        LocalDate today = LocalDate.now();

        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(owner);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAmount(new BigDecimal("80.00"));
        transaction.setCategory("Food");
        transaction.setDescription("Test recommendation");
        transaction.setCurrency("INR");
        transaction.setTransactionDate(today);
        transactionRepository.save(transaction);

        Budget budget = new Budget();
        budget.setUser(owner);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("100.00"));
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setStartDate(today.withDayOfMonth(1));
        budget.setEndDate(today.withDayOfMonth(today.lengthOfMonth()));
        budgetRepository.save(budget);

        mockMvc.perform(get("/API/intelligence/recommendations")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").exists())
                .andExpect(jsonPath("$.periodEnd").exists())
                .andExpect(jsonPath("$.recommendations[0].type").value("BUDGET_THRESHOLD"))
                .andExpect(jsonPath("$.recommendations[0].priority").value("MEDIUM"))
                .andExpect(jsonPath("$.recommendations[0].title").exists())
                .andExpect(jsonPath("$.recommendations[0].explanation").exists())
                .andExpect(jsonPath("$.recommendations[0].evidence").exists())
                .andExpect(jsonPath("$.recommendations[0].suggestedAction").exists());

        mockMvc.perform(get("/API/intelligence/recommendations")
                        .param("userId", owner.getId().toString())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void publicHealthAndAuthenticatedScoreRoutesWork() throws Exception {
        mockMvc.perform(get("/API/healthio"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/API/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Morgan\",\"email\":\"morgan-api@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/API/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"MORGAN-API@example.com\",\"pwd\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = login.getResponse().getContentAsString()
                .replace("{\"token\":\"", "")
                .replace("\"}", "");

        mockMvc.perform(get("/API/intelligence/health-score")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.score").doesNotExist());

        mockMvc.perform(post("/API/intelligence/assistant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How is my budget?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.answer").exists());
    }

    @Test
    void planningRecordsAreIsolatedAcrossAuthenticatedUsers() throws Exception {
        String ownerToken = registerAndLogin("planning-owner@example.com");
        String otherToken = registerAndLogin("planning-other@example.com");

        MvcResult goalCreate = mockMvc.perform(post("/API/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reserve\",\"targetAmount\":1000,\"currentAmount\":250,\"plannedMonthlyContribution\":100,\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long goalId = new tools.jackson.databind.ObjectMapper().readTree(goalCreate.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/API/goals").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Reserve"));
        mockMvc.perform(get("/API/goals").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/API/goals/" + goalId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stolen\",\"targetAmount\":1000,\"currentAmount\":0,\"plannedMonthlyContribution\":0,\"active\":true}"))
                .andExpect(status().isNotFound());

        String liabilityRequest = "{\"name\":\"Card\",\"type\":\"CREDIT_CARD\",\"currentBalance\":500,\"annualInterestRate\":18,\"monthlyPayment\":50,\"asOfDate\":\"" + java.time.LocalDate.now() + "\",\"active\":true}";
        mockMvc.perform(post("/API/liabilities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(liabilityRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/API/liabilities").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void transactionAndBudgetEndpointsEnforceOwnerIsolation() throws Exception {
        String ownerEmail = "record-owner@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        String otherToken = registerAndLogin("record-other@example.com");
        var owner = userRepository.findByEmail(ownerEmail).orElseThrow();

        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(owner);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAmount(new BigDecimal("42.50"));
        transaction.setCategory("Food");
        transaction.setDescription("Lunch");
        transaction.setCurrency("INR");
        transaction.setTransactionDate(LocalDate.now());
        transaction = transactionRepository.save(transaction);

        Budget budget = new Budget();
        budget.setUser(owner);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("500.00"));
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setStartDate(LocalDate.now().withDayOfMonth(1));
        budget.setEndDate(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        budget = budgetRepository.save(budget);

        mockMvc.perform(get("/API/transactions").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(transaction.getId()));
        mockMvc.perform(get("/API/transactions").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/API/transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/API/budgets").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/API/budgets/" + budget.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Food\",\"amount\":600,\"period\":\"MONTHLY\",\"startDate\":\""
                                + LocalDate.now().withDayOfMonth(1) + "\",\"endDate\":\""
                                + LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()) + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountEndpointsEnforceOwnerIsolation() throws Exception {
        String ownerToken = registerAndLogin("account-owner@example.com");
        String otherToken = registerAndLogin("account-other@example.com");
        String accountRequest = "{\"name\":\"Primary\",\"type\":\"SAVINGS\",\"currency\":\"INR\",\"openingBalance\":1000,\"active\":true}";

        MvcResult created = mockMvc.perform(post("/API/accounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(accountRequest))
                .andExpect(status().isCreated()).andReturn();
        Long accountId = new tools.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/API/accounts").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(accountId));
        mockMvc.perform(get("/API/accounts").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/API/accounts/" + accountId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/API/accounts/" + accountId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(accountRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/API/accounts/" + accountId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/API/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Planning Test\",\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/API/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"pwd\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replace("{\"token\":\"", "").replace("\"}", "");
    }
}
