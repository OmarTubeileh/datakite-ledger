package com.datakite.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datakite.ledger.dto.TransactionRequest;
import com.datakite.ledger.service.TransactionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    void ingestReturnsAcceptedAndDelegatesToService() throws Exception {
        String body = """
                {
                  "amount": 129.99,
                  "currency": "USD",
                  "date": "2026-07-01T12:00:00Z",
                  "description": "Subscription fee for AWS Cloud us-east-1"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(transactionService).ingest(any(TransactionRequest.class));
    }

    @Test
    void ingestRejectsInvalidPayload() throws Exception {
        String body = """
                {
                  "amount": -5,
                  "currency": "",
                  "description": ""
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDelegatesToService() throws Exception {
        given(transactionService.listAll()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk());
    }

    @Test
    void analyticsByCategoryDelegatesToService() throws Exception {
        given(transactionService.summarizeByCategory()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/transactions/analytics/by-category"))
                .andExpect(status().isOk());
    }
}
