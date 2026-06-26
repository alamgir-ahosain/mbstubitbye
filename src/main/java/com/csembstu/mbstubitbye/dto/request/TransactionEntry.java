package com.csembstu.mbstubitbye.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntry {

    @NotBlank(message = "transactionId is required in each transaction entry")
    @Size(max = 100, message = "transactionId must not exceed 100 characters")
    private String transactionId;

    @NotBlank(message = "timestamp is required in each transaction entry")
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$",
            message = "timestamp must be a valid ISO-8601 UTC format e.g. 2024-01-15T10:30:00Z"
    )
    private String timestamp;

    @Pattern(
            regexp = "^(transfer|payment|cash_in|settlement)$",
            message = "type must be one of: transfer, payment, cash_in, settlement"
    )
    @NotBlank(message = "type is required in each transaction entry")
    private String type;

    @NotNull(message = "amount is required in each transaction entry")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private Double amount;

    @Size(max = 200, message = "counterparty must not exceed 200 characters")
    @NotBlank(message = "counterparty is required in each transaction entry")
    private String counterparty;

    @Pattern(
            regexp = "^(success|failed|pending|reversed|cancelled)$",
            message = "status must be one of: success, failed, pending, reversed, cancelled"
    )
    @NotBlank(message = "status is required in each transaction entry")
    private String status;
}