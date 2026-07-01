package com.datakite.ledger.service;

import com.datakite.ledger.model.TransactionCategory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Mock rule-based NLP categorization engine: maps a transaction description to
 * one of the five supported categories via keyword matching. Order matters —
 * the first matching category wins, so more specific categories are checked
 * before Operations/Miscellaneous. Used by CategorizationService as the
 * fallback when LLM categorization is unavailable or fails.
 */
@Service
public class RuleBasedCategorizationService {

    private static final Map<TransactionCategory, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(TransactionCategory.INFRASTRUCTURE, List.of(
                "aws", "azure", "gcp", "cloud", "hosting", "server", "ec2", "s3", "datacenter"));
        KEYWORDS.put(TransactionCategory.SAAS_SOFTWARE, List.of(
                "subscription", "saas", "license", "software", "app store", "plan renewal"));
        KEYWORDS.put(TransactionCategory.BUSINESS_MEALS, List.of(
                "restaurant", "lunch", "dinner", "catering", "coffee", "cafe", "breakfast"));
        KEYWORDS.put(TransactionCategory.OPERATIONS, List.of(
                "office", "supplies", "utilities", "rent", "courier", "shipping", "maintenance"));
    }

    public TransactionCategory categorize(String description) {
        String normalized = description.toLowerCase(Locale.ROOT);
        return KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(TransactionCategory.MISCELLANEOUS);
    }
}
