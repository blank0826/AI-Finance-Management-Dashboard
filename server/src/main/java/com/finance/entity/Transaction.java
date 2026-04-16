package com.finance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private StatementUpload upload;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private TransactionType type; // DEBIT or CREDIT

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "original_category")
    @Enumerated(EnumType.STRING)
    private Category originalCategory; // stores AI's suggestion before user edits

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "is_manually_edited")
    private boolean manuallyEdited = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TransactionType {
        DEBIT, CREDIT
    }

    public enum Category {
        FOOD_AND_DINING,
        SHOPPING,
        UTILITIES_AND_BILLS,
        ENTERTAINMENT,
        INVESTMENTS_AND_SAVINGS,
        TRAVEL_AND_TRANSPORT,
        HEALTH_AND_MEDICAL,
        EDUCATION,
        INCOME,
        TRANSFER,
        OTHER
    }
}
