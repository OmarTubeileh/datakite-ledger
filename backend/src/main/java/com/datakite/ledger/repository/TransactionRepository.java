package com.datakite.ledger.repository;

import com.datakite.ledger.model.Transaction;
import com.datakite.ledger.model.TransactionCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT t.category AS category, SUM(t.amount) AS totalAmount, COUNT(t) AS transactionCount
            FROM Transaction t
            WHERE t.category IS NOT NULL
            GROUP BY t.category
            """)
    List<CategoryTotals> findCategoryTotals();

    interface CategoryTotals {
        TransactionCategory getCategory();

        BigDecimal getTotalAmount();

        long getTransactionCount();
    }
}
