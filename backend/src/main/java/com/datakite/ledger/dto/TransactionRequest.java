package com.datakite.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotNull OffsetDateTime date,
        @NotBlank String description
) {
}
