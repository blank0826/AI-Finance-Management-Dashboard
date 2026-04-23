package com.finance.service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.finance.entity.Transaction;

class StatementServiceCategorizeAllTest {

    @Test
    void categorizeAll_usesAiAndFallback() throws Exception {
        StatementService svc = new StatementService();
        GroqService groqService = mock(GroqService.class);

        // Prepare parsed transactions
        PdfParserService.ParsedTransaction p1 = new PdfParserService.ParsedTransaction(
            LocalDate.of(2026,4,1), "Merchant Unknown", BigDecimal.valueOf(100), Transaction.TransactionType.DEBIT);
        PdfParserService.ParsedTransaction p2 = new PdfParserService.ParsedTransaction(
            LocalDate.of(2026,4,2), "Zomato food", BigDecimal.valueOf(200), Transaction.TransactionType.DEBIT);

        // Make AI return categories for both transactions
        when(groqService.categorizeTransactions(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(List.of("OTHER", "FOOD_AND_DINING"));
        when(groqService.parseCategory("OTHER")).thenReturn(Transaction.Category.OTHER);
        when(groqService.parseCategory("FOOD_AND_DINING")).thenReturn(Transaction.Category.FOOD_AND_DINING);

        ReflectionTestUtils.setField(svc, "groqService", groqService);

        Method categorizeAll = StatementService.class.getDeclaredMethod("categorizeAll", java.util.List.class);
        categorizeAll.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.List<Transaction.Category> cats = (java.util.List<Transaction.Category>) categorizeAll.invoke(svc, List.of(p1, p2));

        assertEquals(2, cats.size());
        assertEquals(Transaction.Category.OTHER, cats.get(0));
        assertEquals(Transaction.Category.FOOD_AND_DINING, cats.get(1));
    }
}
