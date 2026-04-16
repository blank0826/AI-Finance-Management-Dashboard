package com.finance.service;

import com.finance.entity.Transaction;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvParserService {

    private static final Logger log = LoggerFactory.getLogger(CsvParserService.class);

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy")
    );

   public List<PdfParserService.ParsedTransaction> parseCsv(MultipartFile file)
        throws IOException, CsvException {
        return parseCsvFromBytes(file.getBytes());
    }

    public List<PdfParserService.ParsedTransaction> parseCsvFromBytes(byte[] bytes)
            throws IOException, CsvException {

        log.debug("Parsing CSV from bytes ({} bytes)", bytes == null ? 0 : bytes.length);
        List<PdfParserService.ParsedTransaction> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(bytes)))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return transactions;

            String[] headers = rows.get(0);
            ColumnMap cols = detectColumns(headers);

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                try {
                    PdfParserService.ParsedTransaction tx = parseRow(row, cols);
                    if (tx != null) transactions.add(tx);
                } catch (Exception ignored) {}
            }
        }

        return transactions;
    }
    
    private ColumnMap detectColumns(String[] headers) {
        ColumnMap map = new ColumnMap();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].toLowerCase().trim();
            if (h.contains("date")) map.dateIdx = i;
            else if (h.contains("narration") || h.contains("description") ||
                     h.contains("particular") || h.contains("details") ||
                     h.contains("remarks") || h.contains("remark")) map.descIdx = i;
            else if (h.contains("debit") || h.contains("withdrawal") || h.contains("dr")) map.debitIdx = i;
            else if (h.contains("credit") || h.contains("deposit") || h.contains("cr")) map.creditIdx = i;
            else if (h.equals("amount") || h.equals("transaction amount")) map.amountIdx = i;
            else if (h.contains("type") || h.contains("txn type")) map.typeIdx = i;
        }
        return map;
    }

    private PdfParserService.ParsedTransaction parseRow(String[] row, ColumnMap cols) {
        if (cols.dateIdx < 0 || cols.dateIdx >= row.length) return null;
        if (cols.descIdx < 0 || cols.descIdx >= row.length) return null;

        String dateStr = row[cols.dateIdx].trim();
        String desc = row[cols.descIdx].trim();
        if (dateStr.isEmpty() || desc.isEmpty()) return null;

        LocalDate date = parseDate(dateStr);
        if (date == null) return null;

        BigDecimal amount;
        Transaction.TransactionType type;

        if (cols.debitIdx >= 0 && cols.creditIdx >= 0) {
            // Separate debit/credit columns
            String debitStr = safeGet(row, cols.debitIdx).replace(",", "").trim();
            String creditStr = safeGet(row, cols.creditIdx).replace(",", "").trim();

            if (!debitStr.isEmpty() && !debitStr.equals("0") && !debitStr.equals("0.00")) {
                amount = new BigDecimal(debitStr);
                type = Transaction.TransactionType.DEBIT;
            } else if (!creditStr.isEmpty() && !creditStr.equals("0") && !creditStr.equals("0.00")) {
                amount = new BigDecimal(creditStr);
                type = Transaction.TransactionType.CREDIT;
            } else {
                return null;
            }
        } else if (cols.amountIdx >= 0) {
            String amountStr = safeGet(row, cols.amountIdx).replace(",", "").trim();
            if (amountStr.isEmpty()) return null;
            boolean negative = amountStr.startsWith("-");
            amount = new BigDecimal(amountStr.replace("-", "")).abs();
            type = negative ? Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;

            if (cols.typeIdx >= 0) {
                String typeStr = safeGet(row, cols.typeIdx).toLowerCase();
                if (typeStr.contains("cr") || typeStr.contains("credit")) {
                    type = Transaction.TransactionType.CREDIT;
                } else if (typeStr.contains("dr") || typeStr.contains("debit")) {
                    type = Transaction.TransactionType.DEBIT;
                }
            }
        } else {
            return null;
        }

        return new PdfParserService.ParsedTransaction(date, desc, amount, type);
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String safeGet(String[] row, int idx) {
        return (idx >= 0 && idx < row.length) ? row[idx] : "";
    }

    private static class ColumnMap {
        int dateIdx = -1, descIdx = -1, debitIdx = -1,
            creditIdx = -1, amountIdx = -1, typeIdx = -1;
    }
}
