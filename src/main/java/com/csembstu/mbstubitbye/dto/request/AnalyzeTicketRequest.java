package com.csembstu.mbstubitbye.dto.request;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeTicketRequest {

    @NotBlank(message = "ticket_id is required and must not be blank")
    private String ticketId;

    @NotBlank(message = "complaint is required and must not be blank")
    @Size(min = 3, max = 5000, message = "complaint must be between 3 and 5000 characters")
    private String complaint;

    private String language;      // en, bn, mixed
    private String channel;       // in_app_chat, call_center, email, merchant_portal, field_agent
    private String userType;      // customer, merchant, agent, unknown
    private String campaignContext;

    @Valid
    private List<TransactionEntry> transactionHistory;

    private Map<String, Object> metadata;
}