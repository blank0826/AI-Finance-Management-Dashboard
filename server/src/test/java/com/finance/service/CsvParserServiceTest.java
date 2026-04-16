package com.finance.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.finance.service.PdfParserService.ParsedTransaction;
import com.opencsv.exceptions.CsvException;

class CsvParserServiceTest {

    @Test
    void parseCsvFromBytes_debitAndCreditColumns() throws IOException, CsvException {
        CsvParserService svc = new CsvParserService();

        String csv = "Date,Description,Debit,Credit\n" +
                     "01/04/2026,Amazon purchase,500,\n" +
                     "02/04/2026,Zomato order,,250\n";

        List<ParsedTransaction> res = svc.parseCsvFromBytes(csv.getBytes());
        assertEquals(2, res.size());

        ParsedTransaction p1 = res.get(0);
        assertEquals("Amazon purchase", p1.description());
        assertEquals(BigDecimal.valueOf(500), p1.amount());

        ParsedTransaction p2 = res.get(1);
        assertEquals("Zomato order", p2.description());
        assertEquals(BigDecimal.valueOf(250), p2.amount());
    }
}
