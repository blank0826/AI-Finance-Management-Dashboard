package com.finance.service;

import com.finance.dto.Dtos.*;
import com.finance.entity.Transaction;
import com.finance.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DashboardService {

    @Autowired private TransactionRepository transactionRepo;
    @Autowired private GroqService groqService;

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    public DashboardSummary getSummary(Long userId, int year, int month) {
        List<Transaction> transactions = transactionRepo.findByUserIdAndYearMonth(userId, year, month);

        BigDecimal totalSpending = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;
        Map<String, BigDecimal> spendingByCategory = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            if (tx.getType() == Transaction.TransactionType.DEBIT) {
                totalSpending = totalSpending.add(tx.getAmount());
                String cat = tx.getCategory().name();
                spendingByCategory.merge(cat, tx.getAmount(), BigDecimal::add);
            } else {
                totalIncome = totalIncome.add(tx.getAmount());
            }
        }

        // Sort categories by amount descending
        spendingByCategory = spendingByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);

        // Monthly trend (last 6 months)
        List<Object[]> trendData = transactionRepo.monthlySpendingTrend(userId);
        List<MonthlyTrend> trend = trendData.stream()
            .map(row -> new MonthlyTrend(
                ((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(),
                new BigDecimal(row[2].toString())))
            .toList();

        // AI summary
        Map<String, Double> categoryDoubles = new LinkedHashMap<>();
        spendingByCategory.forEach((k, v) -> categoryDoubles.put(k, v.doubleValue()));
        String aiSummary = "";
        if (!transactions.isEmpty()) {
            try {
                aiSummary = groqService.generateMonthlySummary(
                    categoryDoubles, totalSpending.doubleValue(),
                    totalIncome.doubleValue(), year, month);
            } catch (Exception e) {
                aiSummary = "Could not generate AI summary at this time.";
            }
        }

        DashboardSummary summary = new DashboardSummary();
        summary.setTotalSpending(totalSpending);
        summary.setTotalIncome(totalIncome);
        summary.setNetSavings(totalIncome.subtract(totalSpending));
        summary.setSpendingByCategory(spendingByCategory);
        summary.setMonthlyTrend(trend);
        summary.setAiSummary(aiSummary);
        summary.setYear(year);
        summary.setMonth(month);
        return summary;
    }
}
