package com.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.finance.entity.Transaction;
import com.finance.entity.User;

@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository txRepo;

    @Autowired
    private UserRepository userRepo;

    @Test
    void findByUserIdOrderAndByCategory() {
        User u = new User();
        u.setEmail("tx.user@example.com");
        u.setName("Tx User");
        u.setPassword("pw");
        u = userRepo.save(u);

        Transaction t1 = new Transaction();
        t1.setUser(u);
        t1.setDescription("First");
        t1.setAmount(BigDecimal.valueOf(100));
        t1.setDate(LocalDate.of(2026,4,1));
        t1.setType(Transaction.TransactionType.DEBIT);
        t1.setCategory(Transaction.Category.FOOD_AND_DINING);

        Transaction t2 = new Transaction();
        t2.setUser(u);
        t2.setDescription("Second");
        t2.setAmount(BigDecimal.valueOf(50));
        t2.setDate(LocalDate.of(2026,4,2));
        t2.setType(Transaction.TransactionType.DEBIT);
        t2.setCategory(Transaction.Category.SHOPPING);

        txRepo.saveAll(List.of(t1, t2));

        List<Transaction> all = txRepo.findByUserIdOrderByDateDesc(u.getId());
        assertEquals(2, all.size());
        assertEquals("Second", all.get(0).getDescription());

        List<Transaction> shopping = txRepo.findByUserIdAndCategoryOrderByDateDesc(u.getId(), Transaction.Category.SHOPPING);
        assertEquals(1, shopping.size());
        assertEquals("Second", shopping.get(0).getDescription());
    }
}
