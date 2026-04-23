package com.finance.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.entity.Transaction;

@Service
public class PdfParserService {


    @Value("${app.groq.api-key:}")
    private String apiKey;

    private static final String GROQ_URL =
        "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Date formatters for post-processing ──────────────────────────────────
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yy",   Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yy",  Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    // ── Entry point ───────────────────────────────────────────────────────────

    public List<ParsedTransaction> parsePdf(MultipartFile file) throws IOException {
        return parsePdfFromBytes(file.getBytes());
    }

    public List<ParsedTransaction> parsePdfFromBytes(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            System.out.println("PDF text length: " + text.length() + " chars");
            System.out.println("PDF pages: " + document.getNumberOfPages());

            if (text.trim().isEmpty()) {
                System.err.println("PDF text is empty — scanned/image PDF not supported");
                return List.of();
            }

            // Detect bank type and route to appropriate parser
            BankType bankType = detectBankType(text);
            System.out.println("Detected bank type: " + bankType);

            return switch (bankType) {
                case PAYTM     -> parsePaytm(text);
                case HDFC      -> parseHdfc(text);
                case SBI       -> parseSbi(text);
                case ICICI     -> parseIcici(text);
                case AXIS      -> parseAxis(text);
                case KOTAK     -> parseKotak(text);
                default        -> parseWithAI(text);  // fallback for unknown banks
            };
        }
    }

    // ── Bank detection ────────────────────────────────────────────────────────

    private enum BankType {
        PAYTM, HDFC, SBI, ICICI, AXIS, KOTAK, UNKNOWN
    }

    private BankType detectBankType(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("paytm statement") ||
            lower.contains("passbook payments history")) return BankType.PAYTM;
        if (lower.contains("hdfc bank") &&
            (lower.contains("withdrawal") || lower.contains("debit"))) return BankType.HDFC;
        if (lower.contains("state bank of india") ||
            lower.contains("sbi ")) return BankType.SBI;
        if (lower.contains("icici bank")) return BankType.ICICI;
        if (lower.contains("axis bank")) return BankType.AXIS;
        if (lower.contains("kotak mahindra") ||
            lower.contains("kotak bank")) return BankType.KOTAK;
        return BankType.UNKNOWN;
    }

    // ── HDFC Bank Parser ──────────────────────────────────────────────────────
    // Format: Date | Narration | Chq/Ref | Value Date | Withdrawal | Deposit | Balance

    private List<ParsedTransaction> parseHdfc(String text) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        // HDFC date pattern: DD/MM/YY followed by narration and amounts
        Pattern pattern = Pattern.compile(
            "(\\d{2}/\\d{2}/\\d{2,4})\\s+(.+?)\\s+" +
            "(?:\\S+\\s+)?(?:\\d{2}/\\d{2}/\\d{2,4}\\s+)?" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s*" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s+" +
            "\\d+[,\\d]*\\.\\d{2}",
            Pattern.DOTALL
        );

        for (String line : text.split("\n")) {
            line = line.trim();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                try {
                    LocalDate date = parseDate(m.group(1));
                    if (date == null) continue;
                    String desc = cleanDescription(m.group(2));
                    String withdrawal = m.group(3);
                    String deposit    = m.group(4);

                    if (withdrawal != null && !withdrawal.isBlank()) {
                        BigDecimal amt = parsAmount(withdrawal);
                        if (amt.compareTo(BigDecimal.ZERO) > 0)
                            transactions.add(new ParsedTransaction(
                                date, desc, amt, Transaction.TransactionType.DEBIT));
                    } else if (deposit != null && !deposit.isBlank()) {
                        BigDecimal amt = parsAmount(deposit);
                        if (amt.compareTo(BigDecimal.ZERO) > 0)
                            transactions.add(new ParsedTransaction(
                                date, desc, amt, Transaction.TransactionType.CREDIT));
                    }
                } catch (Exception ignored) {}
            }
        }

        if (transactions.isEmpty()) return parseWithAI(text);
        System.out.println("HDFC parser found: " + transactions.size() + " transactions");
        return transactions;
    }

    // ── SBI Parser ────────────────────────────────────────────────────────────
    // Format: Txn Date | Value Date | Description | Ref No | Debit | Credit | Balance

    private List<ParsedTransaction> parseSbi(String text) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "(\\d{2}\\s+\\w{3}\\s+\\d{4})\\s+" +  // date: 10 Apr 2024
            "(\\d{2}\\s+\\w{3}\\s+\\d{4}\\s+)?" +  // value date (optional)
            "(.+?)\\s+" +
            "(?:\\S+\\s+)?" +                        // ref no (optional)
            "(\\d+[,\\d]*\\.\\d{2})?\\s*" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s+" +
            "\\d+[,\\d]*\\.\\d{2}"
        );

        for (String line : text.split("\n")) {
            line = line.trim();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                try {
                    LocalDate date = parseDate(m.group(1));
                    if (date == null) continue;
                    String desc   = cleanDescription(m.group(3));
                    String debit  = m.group(4);
                    String credit = m.group(5);

                    if (debit != null && !debit.isBlank()) {
                        transactions.add(new ParsedTransaction(
                            date, desc, parsAmount(debit),
                            Transaction.TransactionType.DEBIT));
                    } else if (credit != null && !credit.isBlank()) {
                        transactions.add(new ParsedTransaction(
                            date, desc, parsAmount(credit),
                            Transaction.TransactionType.CREDIT));
                    }
                } catch (Exception ignored) {}
            }
        }

        if (transactions.isEmpty()) return parseWithAI(text);
        System.out.println("SBI parser found: " + transactions.size() + " transactions");
        return transactions;
    }

    // ── ICICI Parser ──────────────────────────────────────────────────────────
    // Format: S No | Transaction Date | Value Date | Description | Debit | Credit | Balance

    private List<ParsedTransaction> parseIcici(String text) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "(\\d{2}/\\d{2}/\\d{4})\\s+" +
            "(?:\\d{2}/\\d{2}/\\d{4}\\s+)?" +
            "(.+?)\\s+" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s*" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s+" +
            "\\d+[,\\d]*\\.\\d{2}"
        );

        for (String line : text.split("\n")) {
            line = line.trim();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                try {
                    LocalDate date = parseDate(m.group(1));
                    if (date == null) continue;
                    String desc   = cleanDescription(m.group(2));
                    String debit  = m.group(3);
                    String credit = m.group(4);

                    if (desc.toLowerCase().contains("opening") ||
                        desc.toLowerCase().contains("closing")) continue;

                    if (debit != null && !debit.isBlank()) {
                        transactions.add(new ParsedTransaction(
                            date, desc, parsAmount(debit),
                            Transaction.TransactionType.DEBIT));
                    } else if (credit != null && !credit.isBlank()) {
                        transactions.add(new ParsedTransaction(
                            date, desc, parsAmount(credit),
                            Transaction.TransactionType.CREDIT));
                    }
                } catch (Exception ignored) {}
            }
        }

        if (transactions.isEmpty()) return parseWithAI(text);
        System.out.println("ICICI parser found: " + transactions.size() + " transactions");
        return transactions;
    }

    // ── Axis Bank Parser ──────────────────────────────────────────────────────
    // Format: Tran Date | CHQNO | Particulars | DR | CR | BAL

    private List<ParsedTransaction> parseAxis(String text) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "(\\d{2}-\\d{2}-\\d{4})\\s+" +
            "(?:\\S+\\s+)?" +
            "(.+?)\\s+" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s*" +
            "(\\d+[,\\d]*\\.\\d{2})?\\s+" +
            "\\d+[,\\d]*\\.\\d{2}"
        );

        for (String line : text.split("\n")) {
            line = line.trim();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                try {
                    LocalDate date = parseDate(m.group(1));
                    if (date == null) continue;
                    String desc = cleanDescription(m.group(2));
                    String dr   = m.group(3);
                    String cr   = m.group(4);

                    if (dr != null && !dr.isBlank()) {
                        transactions.add(new ParsedTransaction(
                            date, desc, parsAmount(dr),
                            Transaction.TransactionType.DEBIT));
                    } else if (cr != null && !cr.isBlank()) {
                        transactions.add(new ParsedTransaction(
                            date, desc, parsAmount(cr),
                            Transaction.TransactionType.CREDIT));
                    }
                } catch (Exception ignored) {}
            }
        }

        if (transactions.isEmpty()) return parseWithAI(text);
        System.out.println("Axis parser found: " + transactions.size() + " transactions");
        return transactions;
    }

    // ── Kotak Parser ──────────────────────────────────────────────────────────
    // Similar to ICICI — date | description | debit | credit | balance

    private List<ParsedTransaction> parseKotak(String text) {
        // Kotak format is close to ICICI — reuse with DD/MM/YYYY
        return parseIcici(text);
    }

    // ── Paytm Parser ─────────────────────────────────────────────────────────

    private static final Pattern PAYTM_DATE = Pattern.compile(
        "^(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec))$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAYTM_AMOUNT = Pattern.compile(
        "^([+-])\\s*Rs\\.([\\d,]+(?:\\.\\d{1,2})?)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter PAYTM_DATE_FMT =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private List<ParsedTransaction> parsePaytm(String text) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        String[] lines = text.split("\n");
        int currentYear  = LocalDate.now().getYear();
        String currentDate = null;
        String description = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (isPaytmHeader(line)) continue;

            Matcher dateMatcher = PAYTM_DATE.matcher(line);
            if (dateMatcher.matches()) {
                currentDate = line + " " + currentYear;
                description = null;
                continue;
            }

            if (line.matches("\\d{1,2}:\\d{2}\\s*(AM|PM)")) continue;

            Matcher amountMatcher = PAYTM_AMOUNT.matcher(line);
            if (amountMatcher.matches() && currentDate != null && description != null) {
                try {
                    String sign     = amountMatcher.group(1);
                    BigDecimal amt  = parsAmount(amountMatcher.group(2));
                    LocalDate date  = LocalDate.parse(currentDate, PAYTM_DATE_FMT);
                    Transaction.TransactionType type = sign.equals("+")
                        ? Transaction.TransactionType.CREDIT
                        : Transaction.TransactionType.DEBIT;

                    String cleanDesc = description
                        .replaceAll("UPI ID:.*", "").replaceAll("UPI Ref No:.*", "")
                        .replaceAll("Order ID:.*", "").replaceAll("Note:.*", "")
                        .replaceAll("Tag:.*", "").replaceAll("A/c No:.*", "").trim();

                    if (!cleanDesc.isEmpty())
                        transactions.add(new ParsedTransaction(date, cleanDesc, amt, type));

                    description = null;
                } catch (Exception ignored) {}
                continue;
            }

            if (isPaytmMetadata(line)) continue;

            if (currentDate != null) {
                if (description == null) description = line;
                else if (!line.startsWith("Note:") && !line.startsWith("Tag:"))
                    description = description + " " + line;
            }
        }

        System.out.println("Paytm parser found: " + transactions.size() + " transactions");
        return transactions;
    }

    private boolean isPaytmHeader(String line) {
        return line.startsWith("Passbook") || line.startsWith("All payments") ||
               line.startsWith("Date &") || line.startsWith("Time") ||
               line.startsWith("Transaction Details") || line.startsWith("Notes") ||
               line.startsWith("Your Account") || line.startsWith("Amount") ||
               line.startsWith("Page ") || line.startsWith("For any") ||
               line.startsWith("Powered") || line.startsWith("Contact") ||
               line.startsWith("Paytm Statement") || line.startsWith("Total Money") ||
               line.startsWith("Note:") || line.startsWith("Self transfer") ||
               line.startsWith("Payments that") || line.startsWith("Accounts");
    }

    private boolean isPaytmMetadata(String line) {
        return line.startsWith("UPI ID:") || line.startsWith("UPI Ref No:") ||
               line.startsWith("Tag:") || line.startsWith("#") ||
               line.startsWith("Order ID:") || line.startsWith("A/c No:") ||
               line.matches(".*@.*bank.*") || line.matches(".*@ptys.*") ||
               line.matches(".*(HDFC|SBI|ICICI|Axis|Punjab|Kotak|Yes|DCB|Bank).*-\\s*\\d+.*");
    }

    // ── AI-based Parser (fallback for unknown banks) ──────────────────────────
    // Sends raw PDF text to Ollama and asks it to extract transactions as JSON

    private List<ParsedTransaction> parseWithAI(String text) {
        System.out.println("Using AI parser for unknown bank format...");

        // Split into page chunks to avoid token limits
        // Send max 4000 chars per chunk (roughly 2-3 pages)
        List<ParsedTransaction> allTransactions = new ArrayList<>();
        List<String> chunks = splitIntoChunks(text, 1000);

        System.out.println("Splitting PDF into " + chunks.size() + " chunks for AI parsing");

        int maxRetries = 3;
        long baseRetryDelayMs = 500L;

        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("Processing chunk " + (i + 1) + "/" + chunks.size());
            String chunk = chunks.get(i);
            int attempt = 0;
            boolean succeeded = false;

            while (attempt < maxRetries && !succeeded) {
                attempt++;
                try {
                    List<ParsedTransaction> chunkTransactions = extractWithAI(chunk);
                    if (chunkTransactions != null && !chunkTransactions.isEmpty()) {
                        allTransactions.addAll(chunkTransactions);
                        System.out.println("Chunk " + (i + 1) + " parsed successfully (" + chunkTransactions.size() + " txns)");
                    } else {
                        System.out.println("Chunk " + (i + 1) + " parsed but returned 0 transactions");
                    }
                    succeeded = true;
                } catch (Exception e) {
                    System.err.println("AI parsing failed for chunk " + (i + 1) + " attempt " + attempt + ": " + e.getMessage());
                    if (attempt >= maxRetries) {
                        System.err.println("Max retries reached for chunk " + (i + 1));
                    } else {
                        long delay = baseRetryDelayMs * (1L << (attempt - 1));
                        System.out.println("Retrying chunk " + (i + 1) + " after " + delay + "ms");
                        try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }

            // Pause between chunks to respect Ollama
            if (i < chunks.size() - 1) {
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        System.out.println("AI parser found: " + allTransactions.size() + " transactions");
        return allTransactions;
    }

    private List<ParsedTransaction> extractWithAI(String textChunk) {
        String prompt = buildExtractionPrompt(textChunk);
        String response = callOllamaForExtraction(prompt);
        return parseAIExtractionResponse(response);
    }

    private String buildExtractionPrompt(String textChunk) {
        return """
            Extract all bank transactions from this bank statement text.
            
            Return ONLY a JSON array. Each item must have exactly these fields:
            - "date": date in DD/MM/YYYY format
            - "description": merchant or transaction name (clean, no UPI IDs or ref numbers)
            - "amount": number only, no currency symbols or commas
            - "type": either "DEBIT" or "CREDIT"
            
            Rules:
            - DEBIT = money going out (withdrawal, payment, transfer out)
            - CREDIT = money coming in (deposit, salary, refund)
            - Skip header rows, balance rows, opening/closing balance
            - Skip rows without a clear date and amount
            - Clean descriptions: remove UTR, ref numbers, UPI IDs, keep merchant name only
            
            Example output:
            [
              {"date":"10/04/2026","description":"Zomato Food Order","amount":250.00,"type":"DEBIT"},
              {"date":"09/04/2026","description":"Salary Credit","amount":50000.00,"type":"CREDIT"}
            ]
            
            Bank statement text:
            """ + textChunk + """
            
            JSON array only, no explanation:
            """;
    }

    private List<ParsedTransaction> parseAIExtractionResponse(String response) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        try {
            String cleaned = response.trim()
                .replaceAll("(?i)```json", "")
                .replaceAll("```", "")
                .trim();

            int start = cleaned.indexOf('[');
            int end   = cleaned.lastIndexOf(']') + 1;
            if (start < 0 || end <= start) return transactions;

            cleaned = cleaned.substring(start, end);
            JsonNode arr = objectMapper.readTree(cleaned);

            for (JsonNode node : arr) {
                try {
                    String dateStr = node.path("date").asText();
                    String desc    = node.path("description").asText();
                    double amount  = node.path("amount").asDouble();
                    String type    = node.path("type").asText("DEBIT").toUpperCase();

                    if (dateStr.isBlank() || desc.isBlank() || amount <= 0) continue;

                    LocalDate date = parseDate(dateStr);
                    if (date == null) continue;

                    Transaction.TransactionType txType =
                        "CREDIT".equals(type)
                            ? Transaction.TransactionType.CREDIT
                            : Transaction.TransactionType.DEBIT;

                    transactions.add(new ParsedTransaction(
                        date, cleanDescription(desc),
                        BigDecimal.valueOf(amount), txType));

                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            System.err.println("AI response parse failed: " + e.getMessage());
        }
        return transactions;
    }

    private String callOllamaForExtraction(String prompt) {
        try {

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("messages", List.of(
                Map.of("role", "system",
                       "content", "You are a precise data extraction assistant. " +
                                  "Extract data exactly as instructed. Output only JSON."),
                Map.of("role", "user", "content", prompt)
            ));
            body.put("temperature", 0);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(300))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama error: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText();
            if (content.isBlank()) {
                content = root.path("message").path("thinking").asText();
            }
            return content;

        } catch (Exception e) {
            throw new RuntimeException("Ollama extraction failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder chunk = new StringBuilder();

        for (String line : lines) {
            if (chunk.length() + line.length() > chunkSize && chunk.length() > 0) {
                chunks.add(chunk.toString());
                chunk = new StringBuilder();
            }
            chunk.append(line).append("\n");
        }

        if (chunk.length() > 0) chunks.add(chunk.toString());
        return chunks;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        dateStr = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(dateStr, fmt); }
            catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal parsAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amountStr.replace(",", "").trim());
    }

    private String cleanDescription(String desc) {
        if (desc == null) return "";
        return desc
            .replaceAll("\\s+", " ")
            .replaceAll("[A-Z0-9]{15,}", "")   // remove long ref numbers
            .replaceAll("\\d{9,}", "")          // remove long numeric refs
            .replaceAll("UPI/", "")
            .replaceAll("NEFT/", "")
            .replaceAll("IMPS/", "")
            .replaceAll("RTGS/", "")
            .trim();
    }

    public record ParsedTransaction(
        LocalDate date,
        String description,
        BigDecimal amount,
        Transaction.TransactionType type
    ) {}
}