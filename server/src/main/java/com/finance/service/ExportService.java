package com.finance.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.finance.entity.Transaction;
import com.finance.repository.TransactionRepository;

@Service
public class ExportService {

    @Autowired private TransactionRepository transactionRepo;

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── Excel Export ─────────────────────────────────────────────────────────

    public byte[] exportToExcel(Long userId, int year, int month) throws IOException {
        List<Transaction> transactions = transactionRepo.findByUserIdAndYearMonth(userId, year, month);
        log.info("Exporting {} transactions to Excel for user {} {}/{}", transactions.size(), userId, year, month);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createTransactionSheet(workbook, transactions);
            createSummarySheet(workbook, transactions, year, month);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createTransactionSheet(XSSFWorkbook wb, List<Transaction> txns) {
        Sheet sheet = wb.createSheet("Transactions");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle amountStyle = createAmountStyle(wb);

        // Header row
        String[] headers = {"Date", "Description", "Account", "Type", "Category", "Amount (₹)"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowNum = 1;
        for (Transaction tx : txns) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(tx.getDate().format(DATE_FMT));
            row.createCell(1).setCellValue(tx.getDescription());
            row.createCell(2).setCellValue(tx.getAccountName() != null ? tx.getAccountName() : "");
            row.createCell(3).setCellValue(tx.getType().name());
            row.createCell(4).setCellValue(formatCategory(tx.getCategory().name()));
            Cell amtCell = row.createCell(5);
            amtCell.setCellValue(tx.getAmount().doubleValue());
            amtCell.setCellStyle(amountStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void createSummarySheet(XSSFWorkbook wb, List<Transaction> txns, int year, int month) {
        Sheet sheet = wb.createSheet("Summary");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle amountStyle = createAmountStyle(wb);

        String[] months = {"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Financial Summary — " + months[month] + " " + year);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();

        for (Transaction tx : txns) {
            if (tx.getType() == Transaction.TransactionType.DEBIT) {
                totalDebit = totalDebit.add(tx.getAmount());
                byCategory.merge(tx.getCategory().name(), tx.getAmount(), BigDecimal::add);
            } else {
                totalCredit = totalCredit.add(tx.getAmount());
            }
        }

        int r = 2;
        Row h1 = sheet.createRow(r++);
        h1.createCell(0).setCellValue("Metric"); h1.createCell(0).setCellStyle(headerStyle);
        h1.createCell(1).setCellValue("Amount (₹)"); h1.createCell(1).setCellStyle(headerStyle);

        addSummaryRow(sheet, r++, "Total Income", totalCredit, amountStyle);
        addSummaryRow(sheet, r++, "Total Spending", totalDebit, amountStyle);
        addSummaryRow(sheet, r++, "Net Savings", totalCredit.subtract(totalDebit), amountStyle);

        r++;
        Row catHeader = sheet.createRow(r++);
        catHeader.createCell(0).setCellValue("Category"); catHeader.createCell(0).setCellStyle(headerStyle);
        catHeader.createCell(1).setCellValue("Amount (₹)"); catHeader.createCell(1).setCellStyle(headerStyle);

        byCategory.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .forEach(e -> addSummaryRow(sheet, sheet.getLastRowNum() + 1,
                formatCategory(e.getKey()), e.getValue(), amountStyle));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void addSummaryRow(Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    // ── PDF Export ────────────────────────────────────────────────────────────

    public byte[] exportToPdf(Long userId, int year, int month) throws IOException {
        List<Transaction> transactions = transactionRepo.findByUserIdAndYearMonth(userId, year, month);
        String html = buildReportHtml(transactions, year, month);
        log.info("Exporting {} transactions to PDF for user {} {}/{}", transactions.size(), userId, year, month);

        // Use OpenHTMLToPDF
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder =
            new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    private String buildReportHtml(List<Transaction> txns, int year, int month) {
        String[] months = {"","January","February","March","April","May","June",
                           "July","August","September","October","November","December"};

        BigDecimal[] totalDebit = {BigDecimal.ZERO};
        BigDecimal[] totalCredit = {BigDecimal.ZERO};
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();

        for (Transaction tx : txns) {
            if (tx.getType() == Transaction.TransactionType.DEBIT) {
                totalDebit[0] = totalDebit[0].add(tx.getAmount());
                byCategory.merge(tx.getCategory().name(), tx.getAmount(), BigDecimal::add);
            } else {
                totalCredit[0] = totalCredit[0].add(tx.getAmount());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><style>");
        sb.append("body{font-family:Arial,sans-serif;padding:30px;color:#333;}");
        sb.append("h1{color:#1a1a2e;border-bottom:2px solid #4a90e2;padding-bottom:10px;}");
        sb.append("h2{color:#4a90e2;margin-top:30px;}");
        sb.append(".summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;margin:20px 0;}");
        sb.append(".card{background:#f8f9fa;border-radius:8px;padding:15px;text-align:center;}");
        sb.append(".card-label{font-size:12px;color:#666;text-transform:uppercase;}");
        sb.append(".card-value{font-size:22px;font-weight:bold;margin-top:5px;}");
        sb.append(".income{color:#27ae60;} .expense{color:#e74c3c;} .savings{color:#2980b9;}");
        sb.append("table{width:100%;border-collapse:collapse;margin-top:15px;font-size:12px;}");
        sb.append("th{background:#4a90e2;color:white;padding:8px;text-align:left;}");
        sb.append("td{padding:7px 8px;border-bottom:1px solid #eee;}");
        sb.append("tr:nth-child(even){background:#f8f9fa;}");
        sb.append(".cat-bar{background:#4a90e2;height:12px;border-radius:4px;display:inline-block;}");
        sb.append("</style></head><body>");
        sb.append("<h1>Financial Report — ").append(months[month]).append(" ").append(year).append("</h1>");

        // Summary cards
        sb.append("<div class='summary-grid'>");
        sb.append("<div class='card'><div class='card-label'>Total Income</div>")
          .append("<div class='card-value income'>₹").append(String.format("%,.2f", totalCredit[0])).append("</div></div>");
        sb.append("<div class='card'><div class='card-label'>Total Spending</div>")
          .append("<div class='card-value expense'>₹").append(String.format("%,.2f", totalDebit[0])).append("</div></div>");
        BigDecimal savings = totalCredit[0].subtract(totalDebit[0]);
        sb.append("<div class='card'><div class='card-label'>Net Savings</div>")
          .append("<div class='card-value savings'>₹").append(String.format("%,.2f", savings)).append("</div></div>");
        sb.append("</div>");

        // Category breakdown
        sb.append("<h2>Spending by Category</h2>");
        sb.append("<table><tr><th>Category</th><th>Amount</th><th>% of Spending</th></tr>");
        byCategory.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .forEach(e -> {
                double pct = totalDebit[0].doubleValue() > 0
                    ? e.getValue().doubleValue() / totalDebit[0].doubleValue() * 100 : 0;
                sb.append("<tr><td>").append(formatCategory(e.getKey())).append("</td>")
                  .append("<td>₹").append(String.format("%,.2f", e.getValue())).append("</td>")
                  .append("<td>").append(String.format("%.1f", pct)).append("%</td></tr>");
            });
        sb.append("</table>");

        // Transaction list
        sb.append("<h2>All Transactions</h2>");
        sb.append("<table><tr><th>Date</th><th>Description</th><th>Account</th><th>Category</th><th>Type</th><th>Amount</th></tr>");
        for (Transaction tx : txns) {
            sb.append("<tr>")
              .append("<td>").append(tx.getDate().format(DATE_FMT)).append("</td>")
              .append("<td>").append(escapeHtml(tx.getDescription())).append("</td>")
              .append("<td>").append(tx.getAccountName() != null ? escapeHtml(tx.getAccountName()) : "").append("</td>")
              .append("<td>").append(formatCategory(tx.getCategory().name())).append("</td>")
              .append("<td>").append(tx.getType().name()).append("</td>")
              .append("<td>₹").append(String.format("%,.2f", tx.getAmount())).append("</td>")
              .append("</tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String formatCategory(String cat) {
        return Arrays.stream(cat.split("_"))
            .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b).orElse(cat);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createAmountStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }
}
