package com.datakite.ledger.service;

import com.datakite.ledger.model.TransactionCategory;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AI-powered categorization engine: asks an LLM (Groq's OpenAI-compatible API)
 * to classify the transaction description into one of the five supported
 * categories. Falls back to RuleBasedCategorizationService on any failure —
 * missing/invalid API key, network error, rate limit, or an unparseable
 * response — so a categorization outage never blocks transaction processing.
 */
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    private static final String SYSTEM_PROMPT = """
            You are a financial transaction categorization engine. Classify the
            transaction description into exactly one of these five categories:
            SAAS_SOFTWARE, INFRASTRUCTURE, BUSINESS_MEALS, OPERATIONS, MISCELLANEOUS.
            Respond with ONLY the category name, uppercase, nothing else.
            """;

    private final ChatClient chatClient;
    private final RuleBasedCategorizationService fallback;

    public CategorizationService(
            ChatClient.Builder chatClientBuilder, RuleBasedCategorizationService fallback) {
        this.chatClient = chatClientBuilder.build();
        this.fallback = fallback;
    }

    public TransactionCategory categorize(String description) {
        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(description)
                    .call()
                    .content();
            return parse(response);
        } catch (Exception e) {
            log.warn("LLM categorization failed, falling back to rule-based matching: {}", e.getMessage());
            return fallback.categorize(description);
        }
    }

    private TransactionCategory parse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty LLM response");
        }
        String normalized = response.trim().toUpperCase(Locale.ROOT);
        for (TransactionCategory category : TransactionCategory.values()) {
            if (normalized.contains(category.name())) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unrecognized category in LLM response: " + response);
    }
}
