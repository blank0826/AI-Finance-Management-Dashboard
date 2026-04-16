package com.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.entity.Transaction.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class OllamaService {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${app.ollama.model:qwen2.5:7b}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    public record TransactionInput(String description, double amount, String type) {}

    // ── Category map for rule-based pre-filtering ─────────────────────────────
    // Handles obvious cases before sending to AI — reduces AI inconsistency
    private static final Map<String, Category> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        // Food
        KEYWORD_MAP.put("zomato",              Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("swiggy",              Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("compass india",       Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("smartq",              Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("cookieman",           Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("smoor",               Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("oberoi",              Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("tuckshop",            Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("eternal limited",     Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("zomatofood",          Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("malgudi",             Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("gaurav sweets",       Category.FOOD_AND_DINING);
        KEYWORD_MAP.put("all time high hosp",  Category.FOOD_AND_DINING);
        // Entertainment
        KEYWORD_MAP.put("spotify",             Category.ENTERTAINMENT);
        KEYWORD_MAP.put("netflix",             Category.ENTERTAINMENT);
        KEYWORD_MAP.put("hotstar",             Category.ENTERTAINMENT);
        KEYWORD_MAP.put("prime video",         Category.ENTERTAINMENT);
        KEYWORD_MAP.put("bookmyshow",          Category.ENTERTAINMENT);
        // Utilities
        KEYWORD_MAP.put("airtel",              Category.UTILITIES_AND_BILLS);
        KEYWORD_MAP.put("jio",                 Category.UTILITIES_AND_BILLS);
        KEYWORD_MAP.put("adani electricity",   Category.UTILITIES_AND_BILLS);
        KEYWORD_MAP.put("electricity",         Category.UTILITIES_AND_BILLS);
        KEYWORD_MAP.put("rentomojo",           Category.UTILITIES_AND_BILLS);
        KEYWORD_MAP.put("bill payment",        Category.UTILITIES_AND_BILLS);
        // Investments
        KEYWORD_MAP.put("dcb bank",            Category.INVESTMENTS_AND_SAVINGS);
        KEYWORD_MAP.put("mutual fund",         Category.INVESTMENTS_AND_SAVINGS);
        KEYWORD_MAP.put("zerodha",             Category.INVESTMENTS_AND_SAVINGS);
        KEYWORD_MAP.put("groww",               Category.INVESTMENTS_AND_SAVINGS);
        KEYWORD_MAP.put("niyosa",              Category.INVESTMENTS_AND_SAVINGS);
        KEYWORD_MAP.put("orientexchange",      Category.INVESTMENTS_AND_SAVINGS);
        // Health
        KEYWORD_MAP.put("medical",             Category.HEALTH_AND_MEDICAL);
        KEYWORD_MAP.put("chemist",             Category.HEALTH_AND_MEDICAL);
        KEYWORD_MAP.put("pharmacy",            Category.HEALTH_AND_MEDICAL);
        KEYWORD_MAP.put("hospital",            Category.HEALTH_AND_MEDICAL);
        KEYWORD_MAP.put("apollo",              Category.HEALTH_AND_MEDICAL);
        KEYWORD_MAP.put("1mg",                 Category.HEALTH_AND_MEDICAL);
        // Shopping
        KEYWORD_MAP.put("amazon",              Category.SHOPPING);
        KEYWORD_MAP.put("flipkart",            Category.SHOPPING);
        KEYWORD_MAP.put("black box",           Category.SHOPPING);
        KEYWORD_MAP.put("cizer",               Category.SHOPPING);
        KEYWORD_MAP.put("salon",               Category.SHOPPING);
        KEYWORD_MAP.put("crockery",            Category.SHOPPING);
        // Travel
        KEYWORD_MAP.put("uber",                Category.TRAVEL_AND_TRANSPORT);
        KEYWORD_MAP.put("ola",                 Category.TRAVEL_AND_TRANSPORT);
        KEYWORD_MAP.put("rapido",              Category.TRAVEL_AND_TRANSPORT);
        KEYWORD_MAP.put("irctc",               Category.TRAVEL_AND_TRANSPORT);
        KEYWORD_MAP.put("fastag",              Category.TRAVEL_AND_TRANSPORT);
        // Transfer — personal payments
        KEYWORD_MAP.put("money sent to",       Category.TRANSFER);
    }

    // ── Categorization ────────────────────────────────────────────────────────

    public List<String> categorizeTransactions(List<TransactionInput> transactions) {
        List<String> results = new ArrayList<>();
        List<Integer> aiIndexes = new ArrayList<>();
        List<TransactionInput> aiInputs = new ArrayList<>();

        // Step 1: Pre-filter with keyword map — consistent for known merchants
        for (int i = 0; i < transactions.size(); i++) {
            TransactionInput t = transactions.get(i);
            Category keywordCategory = matchKeyword(t.description());

            if (keywordCategory != null) {
                results.add(keywordCategory.name());
                log.debug("Keyword match: {} -> {}", t.description(), keywordCategory);
            } else {
                results.add(null); // placeholder for AI result
                aiIndexes.add(i);
                aiInputs.add(t);
            }
        }

        // Step 2: Send only unmatched transactions to AI
        if (!aiInputs.isEmpty()) {
            log.info("Sending {} transactions to AI...", aiInputs.size());
            List<String> aiResults = callAiForCategories(aiInputs);

            for (int i = 0; i < aiIndexes.size(); i++) {
                int originalIndex = aiIndexes.get(i);
                String aiCategory = i < aiResults.size() ? aiResults.get(i) : "OTHER";
                results.set(originalIndex, aiCategory);
                log.debug("AI categorized: {} -> {}", aiInputs.get(i).description(), aiCategory);
            }
        }

        return results;
    }

    private Category matchKeyword(String description) {
        if (description == null) return null;
        String lower = description.toLowerCase();

        // Check transfer patterns first
        if (lower.startsWith("paid to ") &&
            !lower.contains("zomato") && !lower.contains("swiggy") &&
            !lower.contains("compass") && !lower.contains("smartq") &&
            !lower.contains("bank") && !lower.contains("ltd") &&
            !lower.contains("pvt") && !lower.contains("limited") &&
            !lower.contains("services") && !lower.contains("store") &&
            !lower.contains("shop") && !lower.contains("mart")) {
            return Category.TRANSFER;
        }

        // Check keyword map
        for (Map.Entry<String, Category> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null; // send to AI
    }

    private List<String> callAiForCategories(List<TransactionInput> transactions) {
        String prompt = buildCategorizationPrompt(transactions);
        String response = callOllama(prompt);
        return parseCategories(response, transactions.size());
    }

    private String buildCategorizationPrompt(List<TransactionInput> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Categorize each transaction. Reply with ONLY a JSON array.\n\n");
        sb.append("Categories: FOOD_AND_DINING, SHOPPING, UTILITIES_AND_BILLS, ");
        sb.append("ENTERTAINMENT, INVESTMENTS_AND_SAVINGS, TRAVEL_AND_TRANSPORT, ");
        sb.append("HEALTH_AND_MEDICAL, EDUCATION, TRANSFER, OTHER\n\n");
        sb.append("Rules:\n");
        sb.append("- Payment to a person → TRANSFER\n");
        sb.append("- Food/restaurant/canteen → FOOD_AND_DINING\n");
        sb.append("- Mobile bill/electricity → UTILITIES_AND_BILLS\n");
        sb.append("- Bank/loan/EMI → INVESTMENTS_AND_SAVINGS\n\n");
        sb.append("Output: JSON array with exactly ")
          .append(transactions.size()).append(" strings.\n");
        sb.append("Example: [\"FOOD_AND_DINING\",\"TRANSFER\"]\n\n");

        for (int i = 0; i < transactions.size(); i++) {
            TransactionInput t = transactions.get(i);
            sb.append((i + 1)).append(". ").append(t.description())
              .append(" Rs.").append(String.format("%.0f", t.amount())).append("\n");
        }

        sb.append("\nJSON array only:");
        return sb.toString();
    }

    private List<String> parseCategories(String response, int expectedCount) {
        try {
            String cleaned = response.trim()
                .replaceAll("(?i)```json", "")
                .replaceAll("```", "")
                .trim();

            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']') + 1;

            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end);
            }

            JsonNode arr = objectMapper.readTree(cleaned);
            List<String> categories = new ArrayList<>();
            for (JsonNode node : arr) {
                categories.add(node.asText().trim().toUpperCase());
            }

            while (categories.size() < expectedCount) {
                categories.add("OTHER");
            }

            return categories;

        } catch (Exception e) {
            log.error("Parse failed while parsing categories", e);
            log.debug("Raw response: {}", response);
            List<String> fallback = new ArrayList<>();
            for (int i = 0; i < expectedCount; i++) fallback.add("OTHER");
            return fallback;
        }
    }

    // ── Monthly Summary ───────────────────────────────────────────────────────

    public String generateMonthlySummary(Map<String, Double> spendingByCategory,
                                          double totalSpending, double totalIncome,
                                          int year, int month) {
        String prompt = buildSummaryPrompt(
            spendingByCategory, totalSpending, totalIncome, year, month);
        return callOllama(prompt);
    }

    private String buildSummaryPrompt(Map<String, Double> spendingByCategory,
                                       double totalSpending, double totalIncome,
                                       int year, int month) {
        String[] months = {"","January","February","March","April","May","June",
                           "July","August","September","October","November","December"};
        StringBuilder sb = new StringBuilder();
        sb.append("Write a 3-4 sentence friendly financial summary for ")
          .append(months[month]).append(" ").append(year).append(".\n\n");
        sb.append("Total income: Rs.").append(String.format("%.2f", totalIncome)).append("\n");
        sb.append("Total spending: Rs.").append(String.format("%.2f", totalSpending)).append("\n");
        sb.append("Net savings: Rs.")
          .append(String.format("%.2f", totalIncome - totalSpending)).append("\n");
        sb.append("Breakdown:\n");
        spendingByCategory.forEach((cat, amt) ->
            sb.append("- ").append(cat.replace("_", " "))
              .append(": Rs.").append(String.format("%.2f", amt)).append("\n"));
        sb.append("\nBe encouraging and give one money-saving tip.");
        return sb.toString();
    }

    // ── Ollama API Call ───────────────────────────────────────────────────────

    private String callOllama(String userMessage) {
        try {
            String url = baseUrl + "/api/chat";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                Map.of("role", "system",
                       "content", "You are a precise classification assistant. " +
                                  "Output only what is requested. No explanations."),
                Map.of("role", "user", "content", userMessage)
            ));
            body.put("stream", false);
            body.put("think", false);
            body.put("options", Map.of(
                "temperature", 0,
                "num_predict", 512,
                "num_ctx",     2048
            ));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "Ollama error " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String doneReason = root.path("done_reason").asText();

            if ("length".equals(doneReason)) {
                log.warn("Response truncated by model (done_reason=length) — consider reducing batch size or increasing num_predict");
            }

            String content = root.path("message").path("content").asText();
            if (content.isBlank()) {
                content = root.path("message").path("thinking").asText();
            }

            return content;

        } catch (Exception e) {
            throw new RuntimeException("Ollama API call failed: " + e.getMessage(), e);
        }
    }

    // ── Category Parser ───────────────────────────────────────────────────────

    public Category parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return Category.OTHER;
        try {
            return Category.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String normalized = raw.trim().toUpperCase()
                .replace(" ", "_")
                .replace("&", "AND")
                .replace("-", "_");
            try {
                return Category.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return Category.OTHER;
            }
        }
    }
}