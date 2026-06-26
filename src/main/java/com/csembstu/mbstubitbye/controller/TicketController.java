package com.csembstu.mbstubitbye.controller;


import com.csembstu.mbstubitbye.dto.request.AnalyzeTicketRequest;
import com.csembstu.mbstubitbye.dto.response.AnalyzeTicketResponse;
import com.csembstu.mbstubitbye.service.TicketAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/analyze-ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketAnalysisService ticketAnalysisService;

    @PostMapping
    public ResponseEntity<AnalyzeTicketResponse> analyzeTicket(
            @Valid @RequestBody AnalyzeTicketRequest request) {
        AnalyzeTicketResponse response = ticketAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}