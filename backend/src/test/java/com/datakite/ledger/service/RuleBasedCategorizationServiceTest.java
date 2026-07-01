package com.datakite.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datakite.ledger.model.TransactionCategory;
import org.junit.jupiter.api.Test;

class RuleBasedCategorizationServiceTest {

    private final RuleBasedCategorizationService service = new RuleBasedCategorizationService();

    @Test
    void categorizesInfrastructureKeywords() {
        assertThat(service.categorize("Subscription fee for AWS Cloud us-east-1"))
                .isEqualTo(TransactionCategory.INFRASTRUCTURE);
    }

    @Test
    void categorizesSaasSoftwareKeywords() {
        assertThat(service.categorize("Monthly software license renewal"))
                .isEqualTo(TransactionCategory.SAAS_SOFTWARE);
    }

    @Test
    void categorizesBusinessMealsKeywords() {
        assertThat(service.categorize("Team dinner at a restaurant"))
                .isEqualTo(TransactionCategory.BUSINESS_MEALS);
    }

    @Test
    void categorizesOperationsKeywords() {
        assertThat(service.categorize("Office supplies order"))
                .isEqualTo(TransactionCategory.OPERATIONS);
    }

    @Test
    void fallsBackToMiscellaneousWhenNoKeywordMatches() {
        assertThat(service.categorize("Random unspecified expense item"))
                .isEqualTo(TransactionCategory.MISCELLANEOUS);
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertThat(service.categorize("AWS CLOUD HOSTING INVOICE"))
                .isEqualTo(TransactionCategory.INFRASTRUCTURE);
    }
}
