package com.finance.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.finance.entity.Transaction.Category;
import com.finance.service.OllamaService.TransactionInput;

class OllamaServiceTest {

    @Test
    void categorizeTransactions_keywordAndTransfer() {
        OllamaService svc = new OllamaService();

        List<TransactionInput> inputs = List.of(
            new TransactionInput("Zomato Order #123", 250.0, "DEBIT"),
            new TransactionInput("Paid to John", 500.0, "DEBIT")
        );

        List<String> cats = svc.categorizeTransactions(inputs);
        assertEquals(2, cats.size());
        assertEquals(Category.FOOD_AND_DINING.name(), cats.get(0));
        assertEquals(Category.TRANSFER.name(), cats.get(1));
    }

    @Test
    void parseCategory_variousInputs() {
        OllamaService svc = new OllamaService();
        assertEquals(Category.FOOD_AND_DINING, svc.parseCategory("food_and_dining"));
        assertEquals(Category.FOOD_AND_DINING, svc.parseCategory("Food And Dining"));
        assertEquals(Category.OTHER, svc.parseCategory("nonsense-category"));
        assertEquals(Category.OTHER, svc.parseCategory(null));
    }
}
