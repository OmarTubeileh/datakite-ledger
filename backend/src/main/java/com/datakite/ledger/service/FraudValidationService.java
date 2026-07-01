package com.datakite.ledger.service;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fraud prevention rule: amounts that exceed the configured threshold must be
 * overridden to PENDING_REVIEW before the categorization result is persisted.
 */
@Service
public class FraudValidationService {

    private final BigDecimal reviewThreshold;

    public FraudValidationService(
            @Value("${datakite.ledger.fraud.review-threshold-usd}") BigDecimal reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    public boolean requiresReview(BigDecimal amount) {
        return amount.compareTo(reviewThreshold) > 0;
    }
}
