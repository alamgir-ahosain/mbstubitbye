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
    private String timestamp;


    @NotBlank(message = "type is required in each transaction entry")
    private String type;

    @NotNull(message = "amount is required in each transaction entry")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private Double amount;

    @Size(max = 200, message = "counterparty must not exceed 200 characters")
    @NotBlank(message = "counterparty is required in each transaction entry")
    private String counterparty;


    @NotBlank(message = "status is required in each transaction entry")
    private String status;
}