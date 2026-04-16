package com.finance.repository;

import com.finance.entity.StatementUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatementUploadRepository extends JpaRepository<StatementUpload, Long> {
    List<StatementUpload> findByUserIdOrderByUploadedAtDesc(Long userId);
}
