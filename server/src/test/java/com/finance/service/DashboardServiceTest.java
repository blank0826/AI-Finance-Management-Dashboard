package com.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.finance.dto.Dtos.DashboardSummary;
import com.finance.entity.Transaction;
import com.finance.repository.TransactionRepository;

class DashboardServiceTest {

    @Test
    void getSummary_aggregatesAndAiCalled() {
        TransactionRepository repo = mock(TransactionRepository.class);
        GroqService groqService = mock(GroqService.class);

        Transaction t = new Transaction();
        t.setDate(LocalDate.of(2026,4,1));
        t.setAmount(BigDecimal.valueOf(100));
        t.setType(Transaction.TransactionType.DEBIT);
        t.setCategory(Transaction.Category.FOOD_AND_DINING);

        when(repo.findByUserIdAndYearMonth(1L, 2026, 4)).thenReturn(Arrays.asList(t));
        when(repo.monthlySpendingTrend(1L)).thenReturn(Collections.emptyList());
        when(groqService.generateMonthlySummary(Collections.singletonMap("FOOD_AND_DINING", 100.0), 100.0, 0.0, 2026,4))
            .thenReturn("Good month");

        DashboardService svc = new DashboardService();
        ReflectionTestUtils.setField(svc, "transactionRepo", repo);
        ReflectionTestUtils.setField(svc, "groqService", groqService);

        DashboardSummary s = svc.getSummary(1L, 2026, 4);
        assertEquals(BigDecimal.valueOf(100), s.getTotalSpending());
        assertEquals(BigDecimal.ZERO, s.getTotalIncome());
        assertEquals("Good month", s.getAiSummary());
        assertNotNull(s.getMonthlyTrend());
        assertEquals(0, s.getMonthlyTrend().size());
    }
}
