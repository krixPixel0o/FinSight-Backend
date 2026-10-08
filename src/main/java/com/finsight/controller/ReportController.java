package com.finsight.controller;

import com.finsight.dto.ReportSummaryResponse;
import com.finsight.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/API/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) { this.reportService = reportService; }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(Authentication authentication,
                                        @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate from,
                                        @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate to) {
        return reportService.summary(authentication.getName(), from, to);
    }

    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    public ResponseEntity<String> transactionsCsv(Authentication authentication,
                                                  @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate from,
                                                  @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate to) {
        String csv = reportService.transactionsCsv(authentication.getName(), from, to);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=finsight-transactions.csv")
                .contentType(MediaType.parseMediaType("text/csv")).body(csv);
    }
}
