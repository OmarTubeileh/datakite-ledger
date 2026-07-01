package com.datakite.ledger.dto;

import com.datakite.ledger.model.TransactionCategory;
import java.math.BigDecimal;

public record CategorySummary(
        TransactionCategory category,
        BigDecimal totalAmountUsd,
        long transactionCount
) {
}
