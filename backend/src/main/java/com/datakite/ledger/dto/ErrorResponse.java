package com.datakite.ledger.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        List<FieldErrorDetail> fieldErrors) {
}
