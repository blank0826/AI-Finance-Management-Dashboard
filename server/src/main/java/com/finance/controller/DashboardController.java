package com.finance.controller;

import com.finance.dto.Dtos.*;
import com.finance.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.DashboardService;
import com.finance.service.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private DashboardService dashboardService;
    @Autowired private ExportService exportService;
    @Autowired private UserRepository userRepo;

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    @GetMapping("/summary")
        public ResponseEntity<DashboardSummary> getSummary(
            @RequestParam(name = "year", defaultValue = "0") int year,
            @RequestParam(name = "month", defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        if (year == 0) year = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();

        return ResponseEntity.ok(dashboardService.getSummary(user.getId(), year, month));
    }

    @GetMapping("/export/excel")
        public ResponseEntity<byte[]> exportExcel(
            @RequestParam(name = "year", defaultValue = "0") int year,
            @RequestParam(name = "month", defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        if (year == 0) year = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();

        byte[] data = exportService.exportToExcel(user.getId(), year, month);
        log.info("User {} requested Excel export for {}/{} ({} bytes)", user.getEmail(), year, month, data.length);
        String filename = "finance-report-" + year + "-" + month + ".xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data);
    }

    @GetMapping("/export/pdf")
        public ResponseEntity<byte[]> exportPdf(
            @RequestParam(name = "year", defaultValue = "0") int year,
            @RequestParam(name = "month", defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        if (year == 0) year = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();

        byte[] data = exportService.exportToPdf(user.getId(), year, month);
        log.info("User {} requested PDF export for {}/{} ({} bytes)", user.getEmail(), year, month, data.length);
        String filename = "finance-report-" + year + "-" + month + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.APPLICATION_PDF)
            .body(data);
    }
}
