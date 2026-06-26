package com.csembstu.mbstubitbye.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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

    @Pattern(
            regexp = "^(en|bn|mixed)$",
            message = "language must be one of: en, bn, mixed"
    )
    @NotBlank(message = "language is required and must not be blank")
    private String language;

    @Pattern(
            regexp = "^(in_app_chat|call_center|email|merchant_portal|field_agent)$",
            message = "channel must be one of: in_app_chat, call_center, email, merchant_portal, field_agent"
    )
    @NotBlank(message = "channel is required and must not be blank")
    private String channel;

    @Pattern(
            regexp = "^(customer|merchant|agent|unknown)$",
            message = "userType must be one of: customer, merchant, agent, unknown"
    )
    @NotBlank(message = "userType is required and must not be blank")
    private String userType;

    @Size(max = 500, message = "campaignContext must not exceed 500 characters")
    @Valid
    private String campaignContext;

    @Valid
    @Size(max = 50, message = "transactionHistory must not exceed 50 entries")
    private List<TransactionEntry> transactionHistory;

    private Map<String, Object> metadata;
}