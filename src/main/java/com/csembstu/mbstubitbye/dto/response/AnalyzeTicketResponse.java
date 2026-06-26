package com.csembstu.mbstubitbye.dto.response;


import com.csembstu.mbstubitbye.enums.CaseType;
import com.csembstu.mbstubitbye.enums.Department;
import com.csembstu.mbstubitbye.enums.EvidenceVerdict;
import com.csembstu.mbstubitbye.enums.Severity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalyzeTicketResponse {
    private String ticketId;
    private String relevantTransactionId;   // null if no match
    private EvidenceVerdict evidenceVerdict;
    private CaseType caseType;
    private Severity severity;
    private Department department;
    private String agentSummary;
    private String recommendedNextAction;
    private String customerReply;
    private Boolean humanReviewRequired;
    private Double confidence;
    private List<String> reasonCodes;
}
