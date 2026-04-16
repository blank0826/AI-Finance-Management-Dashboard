package com.finance.dto;

import com.finance.entity.Transaction.Category;
import com.finance.entity.Transaction.TransactionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Dtos {

    // ── Auth ──────────────────────────────────────────────
    @Data
    public static class RegisterRequest {
        @NotBlank @Email
        private String email;
        @NotBlank @Size(min = 6)
        private String password;
        @NotBlank
        private String name;
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String name;
        private String email;

        public AuthResponse(String token, String name, String email) {
            this.token = token;
            this.name = name;
            this.email = email;
        }
    }

    // ── Transaction ───────────────────────────────────────
    @Data
    public static class TransactionDto {
        private Long id;
        private String description;
        private BigDecimal amount;
        private LocalDate date;
        private TransactionType type;
        private Category category;
        private Category originalCategory;
        private String accountName;
        private boolean manuallyEdited;
        private LocalDateTime createdAt;
    }

    @Data
    public static class UpdateCategoryRequest {
        private Category category;
    }

    // ── Upload ────────────────────────────────────────────
    @Data
    public static class UploadResponse {
        private Long uploadId;
        private String status;
        private String message;

        public UploadResponse(Long uploadId, String status, String message) {
            this.uploadId = uploadId;
            this.status = status;
            this.message = message;
        }
    }

    @Data
    public static class UploadStatusDto {
        private Long id;
        private String fileName;
        private String fileType;
        private String accountName;
        private String status;
        private Integer transactionCount;
        private String errorMessage;
        private String fileUrl;
        private LocalDateTime uploadedAt;
    }

    // ── Dashboard ─────────────────────────────────────────
    @Data
    public static class DashboardSummary {
        private BigDecimal totalSpending;
        private BigDecimal totalIncome;
        private BigDecimal netSavings;
        private Map<String, BigDecimal> spendingByCategory;
        private List<MonthlyTrend> monthlyTrend;
        private String aiSummary;
        private int year;
        private int month;
    }

    @Data
    public static class MonthlyTrend {
        private int year;
        private int month;
        private BigDecimal amount;

        public MonthlyTrend(int year, int month, BigDecimal amount) {
            this.year = year;
            this.month = month;
            this.amount = amount;
        }
    }

    @Data
    public static class AddTransactionRequest {
        @NotBlank
        private String description;

        @NotNull
        private BigDecimal amount;

        @NotNull
        private LocalDate date;

        @NotNull
        private TransactionType type;

        private Category category = Category.OTHER;

        private String accountName;
    }
}
