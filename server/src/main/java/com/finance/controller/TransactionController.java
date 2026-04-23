package com.finance.controller;

import com.finance.dto.Dtos.*;
import com.finance.entity.Transaction;
import com.finance.entity.User;
import com.finance.repository.TransactionRepository;
import com.finance.repository.UserRepository;
import com.finance.service.GroqService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired private TransactionRepository transactionRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private GroqService groqService;

    // ── Get all transactions (filtered by month/year) ─────────────────────────

    @GetMapping
    public ResponseEntity<List<TransactionDto>> getAll(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        List<Transaction> txns;

        if (year != null && month != null) {
            txns = transactionRepo.findByUserIdAndYearMonth(user.getId(), year, month);
        } else {
            txns = transactionRepo.findByUserIdOrderByDateDesc(user.getId());
        }

        return ResponseEntity.ok(txns.stream().map(this::toDto).toList());
    }

    // ── Add a new transaction manually ────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> addTransaction(
            @Valid @RequestBody AddTransactionRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();

        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setDescription(req.getDescription());
        tx.setAmount(req.getAmount());
        tx.setDate(req.getDate());
        tx.setType(req.getType());
        tx.setAccountName(req.getAccountName());
        tx.setManuallyEdited(true);
        tx.setCreatedAt(LocalDateTime.now());

        // Use provided category or auto-categorize with Ollama
        if (req.getCategory() != null &&
            req.getCategory() != Transaction.Category.OTHER) {
            tx.setCategory(req.getCategory());
            tx.setOriginalCategory(req.getCategory());
        } else {
            // Auto-categorize using Ollama
            Transaction.Category aiCategory = autoCategorizeSingle(
                req.getDescription(), req.getAmount().doubleValue(), req.getType().name());
            tx.setCategory(aiCategory);
            tx.setOriginalCategory(aiCategory);
        }

        transactionRepo.save(tx);
        return ResponseEntity.ok(toDto(tx));
    }

    // ── Update category ───────────────────────────────────────────────────────

    @PutMapping("/{id}/category")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody UpdateCategoryRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();

        return transactionRepo.findById(id)
            .filter(tx -> tx.getUser().getId().equals(user.getId()))
            .map(tx -> {
                tx.setCategory(req.getCategory());
                tx.setManuallyEdited(true);
                transactionRepo.save(tx);
                return ResponseEntity.ok(toDto(tx));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Delete transaction ────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();

        return transactionRepo.findById(id)
            .filter(tx -> tx.getUser().getId().equals(user.getId()))
            .map(tx -> {
                transactionRepo.delete(tx);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Auto-categorize a single transaction ──────────────────────────────────

    private Transaction.Category autoCategorizeSingle(
            String description, double amount, String type) {
        try {
            List<GroqService.TransactionInput> inputs = List.of(
                new GroqService.TransactionInput(description, amount, type)
            );
            List<String> results = groqService.categorizeTransactions(inputs);
            if (!results.isEmpty()) {
                return groqService.parseCategory(results.get(0));
            }
        } catch (Exception e) {
            System.err.println("Auto-categorization failed: " + e.getMessage());
        }
        return Transaction.Category.OTHER;
    }

    // ── DTO mapper ────────────────────────────────────────────────────────────

    private TransactionDto toDto(Transaction tx) {
        TransactionDto dto = new TransactionDto();
        dto.setId(tx.getId());
        dto.setDescription(tx.getDescription());
        dto.setAmount(tx.getAmount());
        dto.setDate(tx.getDate());
        dto.setType(tx.getType());
        dto.setCategory(tx.getCategory());
        dto.setOriginalCategory(tx.getOriginalCategory());
        dto.setAccountName(tx.getAccountName());
        dto.setManuallyEdited(tx.isManuallyEdited());
        dto.setCreatedAt(tx.getCreatedAt());
        return dto;
    }
}