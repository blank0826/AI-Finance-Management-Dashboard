package com.finance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "statement_uploads")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatementUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "account_name")
    private String accountName;

    @Enumerated(EnumType.STRING)
    private UploadStatus status;

    @Column(name = "transaction_count")
    private Integer transactionCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "upload", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions;

    public enum UploadStatus {
        PROCESSING, COMPLETED, FAILED
    }
}
