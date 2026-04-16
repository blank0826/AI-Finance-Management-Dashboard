package com.finance.controller;

import com.finance.dto.Dtos.*;
import com.finance.entity.StatementUpload;
import com.finance.entity.User;
import com.finance.repository.StatementUploadRepository;
import com.finance.repository.UserRepository;
import com.finance.service.CloudinaryService;
import com.finance.service.StatementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Autowired private StatementService statementService;
    @Autowired private CloudinaryService cloudinaryService;
    @Autowired private StatementUploadRepository uploadRepo;
    @Autowired private UserRepository userRepo;

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "accountName", defaultValue = "My Account") String accountName,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".pdf") &&
                                      !filename.toLowerCase().endsWith(".csv") &&
                                      !filename.toLowerCase().endsWith(".xlsx"))) {
                return ResponseEntity.badRequest().body("Only PDF and CSV files are supported");
            }

            // ── Read bytes HERE in the main thread ──────────────────────────
            // MultipartFile stream closes when the HTTP request ends.
            // The @Async thread starts after the request — too late to read.
            // Reading bytes now guarantees the data is available in the async thread.
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();

            User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
            log.info("Upload requested by user {}: {} ({} bytes)", user.getEmail(), filename, fileBytes.length);
            StatementUpload upload = statementService.createUploadRecord(
                originalFilename, contentType, accountName, user.getId());

            // Pass raw bytes to async method — not the MultipartFile
            statementService.processStatement(
                fileBytes, originalFilename, contentType, upload, user.getId());
            log.info("Created upload record {} and started background processing", upload.getId());

            return ResponseEntity.ok(new UploadResponse(
                upload.getId(), "PROCESSING",
                "File uploaded. AI is categorizing transactions..."));

        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError()
                .body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/status/{uploadId}")
    public ResponseEntity<?> getStatus(
            @PathVariable Long uploadId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        return uploadRepo.findById(uploadId)
            .filter(u -> u.getUser().getId().equals(user.getId()))
            .map(u -> ResponseEntity.ok(toDto(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<UploadStatusDto>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        List<UploadStatusDto> dtos = uploadRepo
            .findByUserIdOrderByUploadedAtDesc(user.getId())
            .stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> deleteUpload(
            @PathVariable Long uploadId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        return uploadRepo.findById(uploadId)
            .filter(u -> u.getUser().getId().equals(user.getId()))
            .map(u -> {
                if (u.getCloudinaryPublicId() != null) {
                    cloudinaryService.deleteFile(u.getCloudinaryPublicId());
                }
                uploadRepo.delete(u);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private UploadStatusDto toDto(StatementUpload u) {
        UploadStatusDto dto = new UploadStatusDto();
        dto.setId(u.getId());
        dto.setFileName(u.getFileName());
        dto.setFileType(u.getFileType());
        dto.setAccountName(u.getAccountName());
        dto.setStatus(u.getStatus().name());
        dto.setTransactionCount(u.getTransactionCount());
        dto.setErrorMessage(u.getErrorMessage());
        dto.setFileUrl(u.getFileUrl());
        dto.setUploadedAt(u.getUploadedAt());
        return dto;
    }
}