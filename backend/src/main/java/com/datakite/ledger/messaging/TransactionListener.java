package com.datakite.ledger.messaging;

import com.datakite.ledger.dto.TransactionRequest;
import com.datakite.ledger.model.Transaction;
import com.datakite.ledger.model.TransactionCategory;
import com.datakite.ledger.model.TransactionStatus;
import com.datakite.ledger.repository.TransactionRepository;
import com.datakite.ledger.service.CategorizationService;
import com.datakite.ledger.service.FraudValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction payloads: runs AI categorization, applies fraud
 * validation, and persists the result.
 *
 * Business rule: the category is still computed for every transaction, but if
 * the amount exceeds the fraud threshold, the persisted status is overridden
 * to PENDING_REVIEW instead of CATEGORIZED — flagging it for human review
 * rather than dropping the categorization result.
 */
@Component
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);

    private final FraudValidationService fraudValidationService;
    private final CategorizationService categorizationService;
    private final TransactionRepository transactionRepository;

    public TransactionListener(
            FraudValidationService fraudValidationService,
            CategorizationService categorizationService,
            TransactionRepository transactionRepository) {
        this.fraudValidationService = fraudValidationService;
        this.categorizationService = categorizationService;
        this.transactionRepository = transactionRepository;
    }

    @JmsListener(destination = "${datakite.ledger.jms.transactions-queue}")
    public void onMessage(TransactionRequest request) {
        TransactionCategory category = categorizationService.categorize(request.description());
        TransactionStatus status = fraudValidationService.requiresReview(request.amount())
                ? TransactionStatus.PENDING_REVIEW
                : TransactionStatus.CATEGORIZED;

        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .currency(request.currency())
                .transactionDate(request.date())
                .description(request.description())
                .status(status)
                .category(category)
                .build();

        transactionRepository.save(transaction);
        log.info("Persisted transaction {} as status={} category={}",
                transaction.getId(), status, category);
    }
}
