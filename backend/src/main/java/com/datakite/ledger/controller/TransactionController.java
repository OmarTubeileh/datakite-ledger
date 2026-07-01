package com.datakite.ledger.controller;

import com.datakite.ledger.dto.CategorySummary;
import com.datakite.ledger.dto.TransactionRequest;
import com.datakite.ledger.dto.TransactionResponse;
import com.datakite.ledger.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@Valid @RequestBody TransactionRequest request) {
        transactionService.ingest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping
    public List<TransactionResponse> list() {
        return transactionService.listAll();
    }

    @GetMapping("/analytics/by-category")
    public List<CategorySummary> analyticsByCategory() {
        return transactionService.summarizeByCategory();
    }
}
