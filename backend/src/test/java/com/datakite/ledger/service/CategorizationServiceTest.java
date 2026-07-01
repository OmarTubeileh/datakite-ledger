package com.datakite.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datakite.ledger.model.TransactionCategory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class CategorizationServiceTest {

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final RuleBasedCategorizationService fallback = new RuleBasedCategorizationService();
    private final CategorizationService service;

    CategorizationServiceTest() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        service = new CategorizationService(builder, fallback);
    }

    private void mockLlmResponse(String content) {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn(content);
    }

    @Test
    void usesLlmResponseWhenValid() {
        // Description would keyword-match INFRASTRUCTURE via the fallback engine;
        // asserting SAAS_SOFTWARE here proves the LLM path (not the fallback) was used.
        mockLlmResponse("SAAS_SOFTWARE");
        assertThat(service.categorize("AWS hosting fee")).isEqualTo(TransactionCategory.SAAS_SOFTWARE);
    }

    @Test
    void parsesLlmResponseWithExtraNoise() {
        mockLlmResponse("Category: BUSINESS_MEALS.");
        assertThat(service.categorize("Some description"))
                .isEqualTo(TransactionCategory.BUSINESS_MEALS);
    }

    @Test
    void fallsBackToRuleBasedWhenLlmThrows() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("simulated API failure"));
        assertThat(service.categorize("Team dinner at a restaurant"))
                .isEqualTo(TransactionCategory.BUSINESS_MEALS);
    }

    @Test
    void fallsBackToRuleBasedWhenLlmResponseIsUnparseable() {
        mockLlmResponse("I cannot classify this transaction.");
        assertThat(service.categorize("Office supplies order"))
                .isEqualTo(TransactionCategory.OPERATIONS);
    }
}
