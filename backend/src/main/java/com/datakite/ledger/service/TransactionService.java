package com.datakite.ledger.service;

import com.datakite.ledger.dto.CategorySummary;
import com.datakite.ledger.dto.TransactionRequest;
import com.datakite.ledger.dto.TransactionResponse;
import com.datakite.ledger.messaging.TransactionProducer;
import com.datakite.ledger.model.Transaction;
import com.datakite.ledger.repository.TransactionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Orchestrates ingest (publish to JMS), the ledger feed, and category analytics
 * queries used by the controller.
 */
@Service
public class TransactionService {

    private final TransactionProducer transactionProducer;
    private final TransactionRepository transactionRepository;

    public TransactionService(
            TransactionProducer transactionProducer, TransactionRepository transactionRepository) {
        this.transactionProducer = transactionProducer;
        this.transactionRepository = transactionRepository;
    }

    public void ingest(TransactionRequest request) {
        transactionProducer.publish(request);
    }

    public List<TransactionResponse> listAll() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TransactionService::toResponse)
                .toList();
    }

    public List<CategorySummary> summarizeByCategory() {
        return transactionRepository.findCategoryTotals().stream()
                .map(row -> new CategorySummary(
                        row.getCategory(), row.getTotalAmount(), row.getTransactionCount()))
                .toList();
    }

    private static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getCategory());
    }
}
