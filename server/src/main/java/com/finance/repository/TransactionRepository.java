package com.finance.repository;

import com.finance.entity.Transaction;
import com.finance.entity.Transaction.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByDateDesc(Long userId);

    List<Transaction> findByUserIdAndDateBetweenOrderByDateDesc(
        Long userId, LocalDate startDate, LocalDate endDate);

    List<Transaction> findByUserIdAndCategoryOrderByDateDesc(Long userId, Category category);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND YEAR(t.date) = :year AND MONTH(t.date) = :month ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndYearMonth(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = 'DEBIT' " +
           "AND YEAR(t.date) = :year AND MONTH(t.date) = :month " +
           "GROUP BY t.category")
    List<Object[]> sumByCategory(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month);

    @Query("SELECT YEAR(t.date), MONTH(t.date), SUM(t.amount) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = 'DEBIT' " +
           "GROUP BY YEAR(t.date), MONTH(t.date) ORDER BY YEAR(t.date), MONTH(t.date)")
    List<Object[]> monthlySpendingTrend(@Param("userId") Long userId);
}
