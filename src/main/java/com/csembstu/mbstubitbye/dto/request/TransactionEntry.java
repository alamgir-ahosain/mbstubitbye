package com.csembstu.mbstubitbye.dto.request;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntry {
    private String transactionId;
    private String timestamp;
    private String type;
    private Double amount;
    private String counterparty;
    private String status;
}