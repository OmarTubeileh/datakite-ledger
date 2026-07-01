package com.datakite.ledger.dto;

import com.datakite.ledger.model.TransactionCategory;
import com.datakite.ledger.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        OffsetDateTime transactionDate,
        String description,
        TransactionStatus status,
        TransactionCategory category
) {
}
