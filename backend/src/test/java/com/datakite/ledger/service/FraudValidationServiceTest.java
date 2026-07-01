package com.datakite.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FraudValidationServiceTest {

    private final FraudValidationService service = new FraudValidationService(BigDecimal.valueOf(5000));

    @Test
    void doesNotFlagAmountBelowThreshold() {
        assertThat(service.requiresReview(BigDecimal.valueOf(4999.99))).isFalse();
    }

    @Test
    void doesNotFlagAmountExactlyAtThreshold() {
        // PDF wording is "exceeds $5,000" — exactly at the threshold must NOT be flagged.
        assertThat(service.requiresReview(BigDecimal.valueOf(5000))).isFalse();
    }

    @Test
    void flagsAmountAboveThreshold() {
        assertThat(service.requiresReview(BigDecimal.valueOf(5000.01))).isTrue();
    }
}
