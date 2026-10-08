package com.finsight.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finsight.dto.DashboardResponse;
import com.finsight.service.DashboardService;

@RestController
@RequestMapping("/API/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse dashboard(Authentication authentication) {
        return dashboardService.getDashboard(authentication.getName());
    }

    @GetMapping("/overview")
    public DashboardResponse overview(Authentication authentication) {
        return dashboardService.getDashboard(authentication.getName());
    }
}