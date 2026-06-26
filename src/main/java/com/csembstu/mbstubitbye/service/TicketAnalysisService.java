package com.csembstu.mbstubitbye.service;



import com.csembstu.mbstubitbye.dto.request.AnalyzeTicketRequest;
import com.csembstu.mbstubitbye.dto.response.AnalyzeTicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAnalysisService {

    private final LlmService llmService;

    public AnalyzeTicketResponse analyze(AnalyzeTicketRequest request) {
        log.info("Analyzing ticket: {}", request.getTicketId());
        AnalyzeTicketResponse response = llmService.analyze(request);
        log.info("Completed ticket: {} → case_type={}, verdict={}",
                request.getTicketId(),
                response.getCaseType(),
                response.getEvidenceVerdict());
        return response;
    }
}