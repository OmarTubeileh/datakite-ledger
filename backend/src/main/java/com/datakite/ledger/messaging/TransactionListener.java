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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction payloads: runs AI categorization, applies fraud
 * validation, and persists the result.
 *
 * Business rule: the category is still computed for every transaction, but if
 * the amount exceeds the fraud threshold, the persisted status is overridden
 * to PENDING_REVIEW instead of CATEGORIZED — flagging it for human review
 * rather than dropping the categorization result.
 *
 * Retry: a failure persisting the transaction (e.g. a transient DB error) is
 * left to propagate rather than swallowed — with the session-transacted JMS
 * listener container, an uncaught exception rolls back the session, so
 * Artemis redelivers the message per the retry/DLQ policy configured in
 * JmsConfig instead of the transaction silently being lost.
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
    public void onMessage(
            TransactionRequest request,
            @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
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

        try {
            transactionRepository.save(transaction);
        } catch (RuntimeException e) {
            // Caught broadly rather than DataAccessException: a connection-acquisition
            // failure (DB down) throws CannotCreateTransactionException, which is a
            // TransactionException, not a DataAccessException — verified live by actually
            // stopping the DB, not just assumed from the exception hierarchy on paper.
            log.warn("Failed to persist transaction (delivery attempt {}): {}",
                    deliveryCount, e.getMessage());
            throw e;
        }

        log.info("Persisted transaction {} as status={} category={}",
                transaction.getId(), status, category);
    }
}
