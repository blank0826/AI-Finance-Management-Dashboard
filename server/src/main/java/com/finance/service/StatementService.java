package com.finance.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.finance.entity.StatementUpload;
import com.finance.entity.Transaction;
import com.finance.entity.User;
import com.finance.repository.StatementUploadRepository;
import com.finance.repository.TransactionRepository;
import com.finance.repository.UserRepository;
import com.finance.service.GroqService.TransactionInput;

@Service
public class StatementService {

    @Autowired private PdfParserService pdfParser;
    @Autowired private CsvParserService csvParser;
    @Autowired private GroqService groqService;
    @Autowired private CloudinaryService cloudinaryService;
    @Autowired private TransactionRepository transactionRepo;
    @Autowired private StatementUploadRepository uploadRepo;
    @Autowired private UserRepository userRepo;

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    private static final int BATCH_SIZE = 20;

    // Called from controller — creates the DB record synchronously
    public StatementUpload createUploadRecord(String filename, String contentType,
                                               String accountName, Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        String fileType = filename != null &&
                          filename.toLowerCase().endsWith(".pdf") ? "PDF" : "CSV";

        StatementUpload upload = new StatementUpload();
        upload.setUser(user);
        upload.setFileName(filename);
        upload.setFileType(fileType);
        upload.setAccountName(accountName);
        upload.setStatus(StatementUpload.UploadStatus.PROCESSING);
        return uploadRepo.save(upload);
    }

    // Async method receives byte[] — never touches MultipartFile
    @Async("taskExecutor")
    public void processStatement(byte[] fileBytes, String filename,
                                  String contentType, StatementUpload upload,
                                  Long userId) {
        try {
            User user = userRepo.findById(userId).orElseThrow();

            log.info("Processing file: {} size: {} bytes", filename, fileBytes.length);

            // ── Step 1: Upload to Cloudinary ─────────────────────────────────
            if (cloudinaryService.isEnabled()) {
                try {
                    String publicId = cloudinaryService.uploadBytes(
                        fileBytes, filename, userId);
                    if (publicId != null) {
                        upload.setCloudinaryPublicId(publicId);
                        upload.setFileUrl(cloudinaryService.getSecureUrl(publicId));
                        uploadRepo.save(upload);
                    }
                } catch (Exception e) {
                    log.error("Cloudinary upload failed", e);
                }
            }

            // ── Step 2: Parse file from bytes ─────────────────────────────────
            List<PdfParserService.ParsedTransaction> parsed;
            if ("PDF".equals(upload.getFileType())) {
                parsed = pdfParser.parsePdfFromBytes(fileBytes);
            } else {
                parsed = csvParser.parseCsvFromBytes(fileBytes);
            }

            log.info("Parsed {} transactions", parsed.size());

            if (parsed.isEmpty()) {
                upload.setStatus(StatementUpload.UploadStatus.FAILED);
                upload.setErrorMessage(
                    "No transactions found. Please check the file format.");
                uploadRepo.save(upload);
                return;
            }

            // ── Step 3: Categorize ────────────────────────────────────────────
            List<Transaction.Category> categories = categorizeAll(parsed);

            // ── Step 4: Save transactions ─────────────────────────────────────
            List<Transaction> transactions = new ArrayList<>();
            for (int i = 0; i < parsed.size(); i++) {
                PdfParserService.ParsedTransaction p = parsed.get(i);
                Transaction.Category cat = i < categories.size()
                    ? categories.get(i) : Transaction.Category.OTHER;

                Transaction tx = new Transaction();
                tx.setUser(user);
                tx.setUpload(upload);
                tx.setDescription(p.description());
                tx.setAmount(p.amount());
                tx.setDate(p.date());
                tx.setType(p.type());
                tx.setCategory(cat);
                tx.setOriginalCategory(cat);
                tx.setAccountName(upload.getAccountName());
                transactions.add(tx);
            }

            transactionRepo.saveAll(transactions);
            upload.setStatus(StatementUpload.UploadStatus.COMPLETED);
            upload.setTransactionCount(transactions.size());
            uploadRepo.save(upload);

            log.info("Successfully processed {} transactions", transactions.size());

        } catch (Exception e) {
            log.error("Processing failed", e);
            upload.setStatus(StatementUpload.UploadStatus.FAILED);
            upload.setErrorMessage("Processing failed: " + e.getMessage());
            uploadRepo.save(upload);
        }
    }

    private List<Transaction.Category> categorizeAll(
            List<PdfParserService.ParsedTransaction> transactions) {

        List<Transaction.Category> allCategories = new ArrayList<>();

        for (int i = 0; i < transactions.size(); i += BATCH_SIZE) {
            List<PdfParserService.ParsedTransaction> batch =
                transactions.subList(i, Math.min(i + BATCH_SIZE, transactions.size()));

            List<TransactionInput> inputs = batch.stream()
                .map(t -> new TransactionInput(
                    t.description(), t.amount().doubleValue(), t.type().name()))
                .toList();

            try {
                List<String> rawCategories = groqService.categorizeTransactions(inputs);
                rawCategories.stream()
                    .map(groqService::parseCategory)
                    .forEach(allCategories::add);

                // // Pause between batches to respect free tier rate limits
                // if (i + BATCH_SIZE < transactions.size()) {
                //     Thread.sleep(5000);
                // }

            } catch (Exception e) {
                log.warn("Ollama failed, using rule-based fallback: {}", e.getMessage(), e);
                batch.stream()
                    .map(t -> ruleBasedCategory(t.description()))
                    .forEach(allCategories::add);
            }
        }

        return allCategories;
    }

    private Transaction.Category ruleBasedCategory(String description) {
        if (description == null) return Transaction.Category.OTHER;
        String d = description.toLowerCase();

        if (d.contains("zomato") || d.contains("swiggy") || d.contains("compass india") ||
            d.contains("smartq") || d.contains("cookieman") || d.contains("food") ||
            d.contains("restaurant") || d.contains("cafe") || d.contains("canteen") ||
            d.contains("kitchen") || d.contains("sweet") || d.contains("bakery") ||
            d.contains("pizza") || d.contains("burger") || d.contains("domino") ||
            d.contains("kfc") || d.contains("mcdonalds") || d.contains("eternal limited") ||
            d.contains("oberoi") || d.contains("tuckshop") || d.contains("smoor") ||
            d.contains("parlour centre"))
            return Transaction.Category.FOOD_AND_DINING;

        if (d.contains("amazon") || d.contains("flipkart") || d.contains("myntra") ||
            d.contains("meesho") || d.contains("nykaa") || d.contains("shopping") ||
            d.contains("mall") || d.contains("salon") || d.contains("black box") ||
            d.contains("crockery") || d.contains("cizer") || d.contains("retail"))
            return Transaction.Category.SHOPPING;

        if (d.contains("airtel") || d.contains("jio") || d.contains("bsnl") ||
            d.contains("electricity") || d.contains("adani") || d.contains("bescom") ||
            d.contains("msedcl") || d.contains("water") || d.contains("gas") ||
            d.contains("bill payment") || d.contains("recharge") ||
            d.contains("rentomojo") || d.contains("tata power"))
            return Transaction.Category.UTILITIES_AND_BILLS;

        if (d.contains("netflix") || d.contains("spotify") || d.contains("hotstar") ||
            d.contains("prime video") || d.contains("youtube") || d.contains("zee5") ||
            d.contains("sonyliv") || d.contains("bookmyshow") || d.contains("pvr") ||
            d.contains("inox") || d.contains("entertainment"))
            return Transaction.Category.ENTERTAINMENT;

        if (d.contains("dcb bank") || d.contains("mutual fund") || d.contains("zerodha") ||
            d.contains("groww") || d.contains("sip") || d.contains("emi") ||
            d.contains("loan") || d.contains("insurance") || d.contains("lic") ||
            d.contains("niyosa") || d.contains("financial service") ||
            d.contains("investment") || d.contains("fd ") || d.contains("ppf"))
            return Transaction.Category.INVESTMENTS_AND_SAVINGS;

        if (d.contains("uber") || d.contains("ola") || d.contains("rapido") ||
            d.contains("irctc") || d.contains("railway") || d.contains("metro") ||
            d.contains("flight") || d.contains("airline") || d.contains("indigo") ||
            d.contains("petrol") || d.contains("fuel") || d.contains("fastag") ||
            d.contains("cab"))
            return Transaction.Category.TRAVEL_AND_TRANSPORT;

        if (d.contains("medical") || d.contains("pharmacy") || d.contains("chemist") ||
            d.contains("hospital") || d.contains("clinic") || d.contains("doctor") ||
            d.contains("apollo") || d.contains("medplus") || d.contains("1mg") ||
            d.contains("pharmeasy") || d.contains("health") || d.contains("nath medical") ||
            d.contains("gokul chemist"))
            return Transaction.Category.HEALTH_AND_MEDICAL;

        if (d.contains("school") || d.contains("college") || d.contains("university") ||
            d.contains("course") || d.contains("udemy") || d.contains("coursera") ||
            d.contains("byju") || d.contains("unacademy") || d.contains("fees") ||
            d.contains("tuition") || d.contains("education"))
            return Transaction.Category.EDUCATION;

        if (d.startsWith("paid to ") || d.startsWith("money sent to ") ||
            d.contains("upi transfer") || d.contains("neft") || d.contains("imps") ||
            d.contains("money transfer"))
            return Transaction.Category.TRANSFER;

        return Transaction.Category.OTHER;
    }
}