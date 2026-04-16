package com.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.finance.entity.Transaction;
import com.finance.repository.TransactionRepository;

class ExportServiceTest {

    @Test
    void exportToExcel_returnsNonEmptyBytes() throws Exception {
        TransactionRepository repo = mock(TransactionRepository.class);
        ExportService svc = new ExportService();
        ReflectionTestUtils.setField(svc, "transactionRepo", repo);

        Transaction t = new Transaction();
        t.setDate(LocalDate.of(2026,4,1));
        t.setDescription("Sample");
        t.setAmount(BigDecimal.valueOf(123.45));
        t.setType(Transaction.TransactionType.DEBIT);
        t.setCategory(Transaction.Category.OTHER);

        when(repo.findByUserIdAndYearMonth(1L, 2026, 4)).thenReturn(List.of(t));

        byte[] bytes = svc.exportToExcel(1L, 2026, 4);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }
}
