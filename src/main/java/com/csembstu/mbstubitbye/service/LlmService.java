package com.csembstu.mbstubitbye.service;

import com.csembstu.mbstubitbye.dto.request.AnalyzeTicketRequest;
import com.csembstu.mbstubitbye.dto.request.TransactionEntry;
import com.csembstu.mbstubitbye.dto.response.AnalyzeTicketResponse;
import com.csembstu.mbstubitbye.enums.CaseType;
import com.csembstu.mbstubitbye.enums.Department;
import com.csembstu.mbstubitbye.enums.EvidenceVerdict;
import com.csembstu.mbstubitbye.enums.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure rule-based ticket analyzer — no LLM required.
 *
 * Pipeline:
 *   1. Detect case_type from complaint keywords (priority order).
 *   2. Match relevant transaction from history using amount + type + status signals.
 *   3. Determine evidence_verdict by cross-checking complaint against matched transaction.
 *   4. Derive severity, department, human_review_required from case_type + evidence.
 *   5. Generate safe agent_summary, recommended_next_action, and customer_reply.
 */
@Slf4j
@Service
public class LlmService {

    // ─────────────────────────────────────────────────────────────────────────
    // Keyword lists
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<String> PHISHING_KW = List.of(
            "otp", "pin", "password", "পিন", "ওটিপি", "পাসওয়ার্ড",
            "asked for", "asking for", "someone called", "fraud call",
            "scam", "impersonat", "bkash agent called", "verify your",
            "share your", "send your otp", "told me to share",
            "account will be blocked", "will be suspended", "verify account",
            "claim to be", "claiming to be", "posing as"
    );

    private static final List<String> WRONG_TRANSFER_KW = List.of(
            "wrong number", "wrong account", "wrong recipient", "wrong person",
            "wrong merchant", "ভুল নম্বর", "ভুল একাউন্ট", "ভুল মার্চেন্ট",
            "sent to wrong", "transferred to wrong", "transfer to wrong",
            "sent wrong", "wrong transfer", "mistakenly sent", "accidentally sent",
            "accidentally transferred", "mistakenly transferred",
            "get it back", "want it back", "return my money", "recover",
            "typed it wrong", "wrong e pathiye", "wrong e transfer"
    );

    private static final List<String> DUPLICATE_PAYMENT_KW = List.of(
            "charged twice", "deducted twice", "double charge", "duplicate",
            "two times", "two payments", "paid twice", "double deduction",
            "same amount deducted", "charged two times", "deducted two times"
    );

    private static final List<String> MERCHANT_SETTLEMENT_KW = List.of(
            "settlement", "not settled", "settlement delay", "settlement not received",
            "merchant payment", "sales not credited", "merchant account",
            "settlement pending", "disbursement", "পেমেন্ট আসেনি", "সেটেলমেন্ট"
    );

    private static final List<String> AGENT_CASH_IN_KW = List.of(
            "cash in", "cash-in", "agent", "এজেন্ট", "ক্যাশ ইন",
            "deposited through agent", "agent deposited", "agent sent",
            "cash deposit", "balance not updated", "agent says sent but",
            "not reflected", "did not reflect", "not credited after agent"
    );

    private static final List<String> PAYMENT_FAILED_KW = List.of(
            "payment failed", "transaction failed", "failed but deducted",
            "deducted", "balance deducted", "money deducted",
            "পেমেন্ট ফেল", "ট্রানজেকশন ফেল", "কাটা গেছে", "কেটে নিয়েছে",
            "failed transaction", "payment not", "did not go through",
            "never received the money", "never received", "did not receive",
            "haven't received", "paid but", "not credited", "balance was cut"
    );

    private static final List<String> REFUND_KW = List.of(
            "refund", "money back", "cancel", "রিফান্ড",
            "changed my mind", "want refund", "please refund",
            "give me back", "don't want", "do not want"
    );

    // Amount pattern  e.g. "5000 taka", "৳2000", "tk 1500"
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d[\\d,]*)");

    // ─────────────────────────────────────────────────────────────────────────
    // Main entry point (called by TicketAnalysisService — same as before)
    // ─────────────────────────────────────────────────────────────────────────

    public AnalyzeTicketResponse analyze(AnalyzeTicketRequest req) {
        try {
            return doAnalyze(req);
        } catch (Exception e) {
            log.error("Rule-based analysis failed: {}", e.getMessage(), e);
            return buildFallbackResponse(req.getTicketId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core pipeline
    // ─────────────────────────────────────────────────────────────────────────

    private AnalyzeTicketResponse doAnalyze(AnalyzeTicketRequest req) {
        String complaint = req.getComplaint() == null ? "" : req.getComplaint();
        String lower     = complaint.toLowerCase(Locale.ROOT);
        boolean isBangla = "bn".equalsIgnoreCase(req.getLanguage())
                || detectBangla(complaint);
        boolean isMerchant = "merchant".equalsIgnoreCase(req.getUserType());
        boolean isAgent    = "agent".equalsIgnoreCase(req.getUserType());

        List<TransactionEntry> history = req.getTransactionHistory() != null
                ? req.getTransactionHistory() : List.of();

        // ── Step 1: Classify ──────────────────────────────────────────────
        CaseType caseType = classifyCase(lower, isMerchant, isAgent, history);

        // ── Step 2: Find relevant transaction ────────────────────────────
        TransactionMatch match = findRelevantTransaction(caseType, lower, history);
        String relevantTxnId = match.transactionId;

        // ── Step 3: Evidence verdict ──────────────────────────────────────
        EvidenceVerdict verdict = computeVerdict(caseType, lower, match, history);

        // ── Step 4: Severity & department ─────────────────────────────────
        Severity severity   = computeSeverity(caseType, lower, match);
        Department dept     = computeDepartment(caseType, verdict, req);

        // ── Step 5: human_review_required ────────────────────────────────
        boolean humanReview = needsHumanReview(caseType, severity, verdict);

        // ── Step 6: Textual fields ────────────────────────────────────────
        String agentSummary      = buildAgentSummary(caseType, complaint, relevantTxnId, match, verdict);
        String nextAction        = buildNextAction(caseType, relevantTxnId, verdict, match);
        String customerReply     = buildCustomerReply(caseType, relevantTxnId, isBangla, isMerchant);
        List<String> reasonCodes = buildReasonCodes(caseType, verdict, match);
        double confidence        = computeConfidence(caseType, verdict, match, history);

        return AnalyzeTicketResponse.builder()
                .ticketId(req.getTicketId())
                .relevantTransactionId(relevantTxnId)
                .evidenceVerdict(verdict)
                .caseType(caseType)
                .severity(severity)
                .department(dept)
                .agentSummary(agentSummary)
                .recommendedNextAction(nextAction)
                .customerReply(customerReply)
                .humanReviewRequired(humanReview)
                .confidence(confidence)
                .reasonCodes(reasonCodes)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1 — Case classification (keyword priority order)
    // ─────────────────────────────────────────────────────────────────────────

    private CaseType classifyCase(String lower, boolean isMerchant, boolean isAgent,
                                  List<TransactionEntry> history) {

        // Phishing — always highest priority
        if (containsAny(lower, PHISHING_KW)) return CaseType.PHISHING_OR_SOCIAL_ENGINEERING;

        // Duplicate payment — must check before payment_failed / wrong_transfer
        if (containsAny(lower, DUPLICATE_PAYMENT_KW)) return CaseType.DUPLICATE_PAYMENT;
        // Also detect duplicate via transaction history (two identical amount+counterparty within 60s)
        if (hasDuplicateInHistory(history)) return CaseType.DUPLICATE_PAYMENT;

        // Wrong transfer
        if (containsAny(lower, WRONG_TRANSFER_KW)) return CaseType.WRONG_TRANSFER;

        // Merchant settlement
        if (isMerchant || containsAny(lower, MERCHANT_SETTLEMENT_KW)) {
            // only if there's a settlement type in history or keywords match
            if (containsAny(lower, MERCHANT_SETTLEMENT_KW)
                    || historyHasType(history, "settlement")) {
                return CaseType.MERCHANT_SETTLEMENT_DELAY;
            }
        }

        // Agent cash-in
        if (isAgent || containsAny(lower, AGENT_CASH_IN_KW)) {
            if (containsAny(lower, AGENT_CASH_IN_KW)
                    || historyHasType(history, "cash_in")) {
                return CaseType.AGENT_CASH_IN_ISSUE;
            }
        }

        // Payment failed
        if (containsAny(lower, PAYMENT_FAILED_KW)) return CaseType.PAYMENT_FAILED;

        // Refund
        if (containsAny(lower, REFUND_KW)) return CaseType.REFUND_REQUEST;

        return CaseType.OTHER;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 — Find the relevant transaction
    // ─────────────────────────────────────────────────────────────────────────

    private TransactionMatch findRelevantTransaction(CaseType caseType, String lower,
                                                     List<TransactionEntry> history) {
        if (history == null || history.isEmpty()) return TransactionMatch.none();

        long mentionedAmount = extractMaxAmount(lower);

        // Special: duplicate payment — return the SECOND matching transaction
        if (caseType == CaseType.DUPLICATE_PAYMENT) {
            Optional<String> dupId = findDuplicateTransactionId(history);
            if (dupId.isPresent()) return new TransactionMatch(dupId.get(), -1, true);
        }

        // Special: merchant settlement
        if (caseType == CaseType.MERCHANT_SETTLEMENT_DELAY) {
            return history.stream()
                    .filter(t -> "settlement".equalsIgnoreCase(t.getType()))
                    .findFirst()
                    .map(t -> new TransactionMatch(t.getTransactionId(),
                            t.getAmount() != null ? t.getAmount().longValue() : 0, false))
                    .orElse(TransactionMatch.none());
        }

        // Special: agent cash-in
        if (caseType == CaseType.AGENT_CASH_IN_ISSUE) {
            return history.stream()
                    .filter(t -> "cash_in".equalsIgnoreCase(t.getType()))
                    .findFirst()
                    .map(t -> new TransactionMatch(t.getTransactionId(),
                            t.getAmount() != null ? t.getAmount().longValue() : 0, false))
                    .orElse(TransactionMatch.none());
        }

        // General: match by amount if mentioned, else take most recent transfer/payment
        List<TransactionEntry> candidates = history.stream()
                .filter(t -> isRelevantType(t, caseType))
                .toList();

        if (candidates.isEmpty()) return TransactionMatch.none();

        if (mentionedAmount > 0) {
            List<TransactionEntry> amountMatches = candidates.stream()
                    .filter(t -> t.getAmount() != null
                            && Math.round(t.getAmount()) == mentionedAmount)
                    .toList();

            if (amountMatches.size() == 1) {
                TransactionEntry t = amountMatches.get(0);
                return new TransactionMatch(t.getTransactionId(),
                        t.getAmount().longValue(), false);
            }
            if (amountMatches.size() > 1) {
                // Ambiguous — cannot determine
                return TransactionMatch.ambiguous();
            }
        }

        // No amount or no match — take most recent candidate
        if (candidates.size() == 1) {
            TransactionEntry t = candidates.get(0);
            return new TransactionMatch(t.getTransactionId(),
                    t.getAmount() != null ? t.getAmount().longValue() : 0, false);
        }

        // Multiple candidates without clear amount match — ambiguous
        return TransactionMatch.ambiguous();
    }

    private boolean isRelevantType(TransactionEntry t, CaseType caseType) {
        String type = t.getType() == null ? "" : t.getType().toLowerCase();
        return switch (caseType) {
            case WRONG_TRANSFER -> "transfer".equals(type);
            case PAYMENT_FAILED, DUPLICATE_PAYMENT, REFUND_REQUEST -> "payment".equals(type) || "transfer".equals(type);
            default -> true;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 — Evidence verdict
    // ─────────────────────────────────────────────────────────────────────────

    private EvidenceVerdict computeVerdict(CaseType caseType, String lower,
                                           TransactionMatch match,
                                           List<TransactionEntry> history) {

        if (caseType == CaseType.PHISHING_OR_SOCIAL_ENGINEERING) {
            return EvidenceVerdict.INSUFFICIENT_DATA; // no transaction evidence expected
        }

        if (match.isAmbiguous) return EvidenceVerdict.INSUFFICIENT_DATA;

        if (match.transactionId == null) {
            // No matching transaction found in history
            return history.isEmpty()
                    ? EvidenceVerdict.INSUFFICIENT_DATA
                    : EvidenceVerdict.INSUFFICIENT_DATA;
        }

        // Inconsistency check for wrong_transfer:
        // If multiple past transfers to the SAME counterparty exist → inconsistent
        if (caseType == CaseType.WRONG_TRANSFER) {
            TransactionEntry matched = history.stream()
                    .filter(t -> t.getTransactionId().equals(match.transactionId))
                    .findFirst().orElse(null);
            if (matched != null) {
                long priorCount = history.stream()
                        .filter(t -> !t.getTransactionId().equals(match.transactionId))
                        .filter(t -> matched.getCounterparty() != null
                                && matched.getCounterparty().equals(t.getCounterparty()))
                        .count();
                if (priorCount >= 2) return EvidenceVerdict.INCONSISTENT;
            }
        }

        // Duplicate payment: two identical transactions = consistent
        if (caseType == CaseType.DUPLICATE_PAYMENT && match.isDuplicate) {
            return EvidenceVerdict.CONSISTENT;
        }

        // Default: transaction found → consistent
        return EvidenceVerdict.CONSISTENT;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4a — Severity
    // ─────────────────────────────────────────────────────────────────────────

    private Severity computeSeverity(CaseType caseType, String lower, TransactionMatch match) {
        return switch (caseType) {
            case PHISHING_OR_SOCIAL_ENGINEERING -> Severity.CRITICAL;
            case WRONG_TRANSFER -> {
                long amt = match.amount > 0 ? match.amount : extractMaxAmount(lower);
                yield amt >= 10000 ? Severity.CRITICAL : Severity.HIGH;
            }
            case PAYMENT_FAILED, DUPLICATE_PAYMENT, AGENT_CASH_IN_ISSUE -> Severity.HIGH;
            case MERCHANT_SETTLEMENT_DELAY -> Severity.MEDIUM;
            case REFUND_REQUEST -> Severity.LOW;
            default -> Severity.LOW;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4b — Department
    // ─────────────────────────────────────────────────────────────────────────

    private Department computeDepartment(CaseType caseType, EvidenceVerdict verdict,
                                         AnalyzeTicketRequest req) {
        return switch (caseType) {
            case WRONG_TRANSFER -> Department.DISPUTE_RESOLUTION;
            case PAYMENT_FAILED, DUPLICATE_PAYMENT -> Department.PAYMENTS_OPS;
            case MERCHANT_SETTLEMENT_DELAY -> Department.MERCHANT_OPERATIONS;
            case AGENT_CASH_IN_ISSUE -> Department.AGENT_OPERATIONS;
            case PHISHING_OR_SOCIAL_ENGINEERING -> Department.FRAUD_RISK;
            case REFUND_REQUEST -> {
                // Contested refund → dispute_resolution; change-of-mind → customer_support
                String lower = req.getComplaint() == null ? "" : req.getComplaint().toLowerCase();
                boolean contested = lower.contains("dispute") || lower.contains("wrong")
                        || lower.contains("unauthorized") || lower.contains("did not order");
                yield contested ? Department.DISPUTE_RESOLUTION : Department.CUSTOMER_SUPPORT;
            }
            default -> Department.CUSTOMER_SUPPORT;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 5 — Human review flag
    // ─────────────────────────────────────────────────────────────────────────

    private boolean needsHumanReview(CaseType caseType, Severity severity, EvidenceVerdict verdict) {
        if (caseType == CaseType.PHISHING_OR_SOCIAL_ENGINEERING) return true;
        if (severity == Severity.CRITICAL || severity == Severity.HIGH) return true;
        if (verdict == EvidenceVerdict.INCONSISTENT) return true;
        if (caseType == CaseType.DUPLICATE_PAYMENT) return true;
        if (caseType == CaseType.WRONG_TRANSFER) return true;
        if (caseType == CaseType.AGENT_CASH_IN_ISSUE) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 6 — Text generation
    // ─────────────────────────────────────────────────────────────────────────

    private String buildAgentSummary(CaseType caseType, String complaint,
                                     String txnId, TransactionMatch match,
                                     EvidenceVerdict verdict) {
        String txnRef = txnId != null ? " (" + txnId + ")" : "";
        String amtRef = match.amount > 0 ? " of " + match.amount + " BDT" : "";

        return switch (caseType) {
            case PHISHING_OR_SOCIAL_ENGINEERING ->
                    "Customer reports a suspicious contact possibly attempting to obtain sensitive credentials. No credential was shared yet.";
            case WRONG_TRANSFER ->
                    "Customer reports a wrong transfer" + amtRef + txnRef + ". " +
                            (verdict == EvidenceVerdict.INCONSISTENT
                                    ? "Evidence is inconsistent — prior transfers to the same recipient exist."
                                    : "Transaction history supports the claim.");
            case PAYMENT_FAILED ->
                    "Customer reports a failed payment" + amtRef + txnRef +
                            " where balance may have been deducted. Requires payments ops investigation.";
            case DUPLICATE_PAYMENT ->
                    "Customer reports a duplicate payment" + amtRef + ". " +
                            "Two identical transactions detected in history" + txnRef + ".";
            case MERCHANT_SETTLEMENT_DELAY ->
                    "Merchant reports settlement" + amtRef + txnRef + " not received within expected SLA window. Status is pending.";
            case AGENT_CASH_IN_ISSUE ->
                    "Customer reports cash-in" + amtRef + " via agent not reflected in balance" + txnRef + ".";
            case REFUND_REQUEST ->
                    "Customer is requesting a refund for a recent transaction" + txnRef + ".";
            default ->
                    "Customer submitted a general inquiry or concern. Insufficient detail to classify further.";
        };
    }

    private String buildNextAction(CaseType caseType, String txnId,
                                   EvidenceVerdict verdict, TransactionMatch match) {
        String ref = txnId != null ? txnId : "the reported transaction";
        return switch (caseType) {
            case PHISHING_OR_SOCIAL_ENGINEERING ->
                    "Escalate to fraud_risk team immediately. Log reported number for fraud pattern analysis. Confirm to customer that the company never requests OTP.";
            case WRONG_TRANSFER ->
                    verdict == EvidenceVerdict.INCONSISTENT
                            ? "Flag for human review. Verify with customer whether this was a genuine wrong transfer given the established transfer pattern with this recipient."
                            : "Verify " + ref + " details with customer and initiate the wrong-transfer dispute workflow per policy.";
            case PAYMENT_FAILED ->
                    "Investigate " + ref + " ledger status. If balance was deducted on a failed payment, initiate the automatic reversal flow within standard SLA.";
            case DUPLICATE_PAYMENT ->
                    "Verify the duplicate with payments_ops. If the biller/merchant confirms only one payment was received, initiate reversal of " + ref + ".";
            case MERCHANT_SETTLEMENT_DELAY ->
                    "Route to merchant_operations to verify settlement batch status. If batch is delayed, communicate a revised ETA to the merchant.";
            case AGENT_CASH_IN_ISSUE ->
                    "Investigate " + ref + " pending status with agent operations. Confirm settlement state and resolve within the standard cash-in SLA.";
            case REFUND_REQUEST ->
                    "Inform the customer that refund eligibility depends on the merchant's own policy. Provide guidance on contacting the merchant directly.";
            default ->
                    "Reply to customer asking for specific details: which transaction, the amount, and what went wrong. Do not initiate any dispute until details are confirmed.";
        };
    }

    private String buildCustomerReply(CaseType caseType, String txnId,
                                      boolean isBangla, boolean isMerchant) {
        String ref = txnId != null ? "transaction " + txnId : "your reported transaction";

        if (isBangla) {
            // Bangla replies for Bangla-speaking customers
            return switch (caseType) {
                case PHISHING_OR_SOCIAL_ENGINEERING ->
                        "আপনি কোনো তথ্য শেয়ার করার আগে আমাদের সাথে যোগাযোগ করার জন্য ধন্যবাদ। আমরা কখনও কোনো পরিস্থিতিতে আপনার পিন, ওটিপি বা পাসওয়ার্ড চাই না। অনুগ্রহ করে এগুলো কারো সাথে শেয়ার করবেন না। আমাদের ফ্রড টিমকে এই ঘটনা সম্পর্কে অবহিত করা হয়েছে।";
                case AGENT_CASH_IN_ISSUE ->
                        "আপনার লেনদেন " + (txnId != null ? txnId : "") + " এর বিষয়ে আমরা অবগত হয়েছি। আমাদের এজেন্ট অপারেশন্স দল এটি দ্রুত যাচাই করবে এবং অফিশিয়াল চ্যানেলে আপনাকে জানাবে। অনুগ্রহ করে কারো সাথে আপনার পিন বা ওটিপি শেয়ার করবেন না।";
                default ->
                        "আপনার অভিযোগ সম্পর্কে আমরা অবগত হয়েছি। আমাদের দল বিষয়টি পর্যালোচনা করবে এবং অফিশিয়াল চ্যানেলে আপনাকে জানাবে। অনুগ্রহ করে কারো সাথে আপনার পিন বা ওটিপি শেয়ার করবেন না।";
            };
        }

        return switch (caseType) {
            case PHISHING_OR_SOCIAL_ENGINEERING ->
                    "Thank you for reaching out before sharing any information. " +
                            "We never ask for your PIN, OTP, or password under any circumstances. " +
                            "Please do not share these with anyone, even if they claim to be from us. " +
                            "Our fraud team has been notified of this incident.";
            case WRONG_TRANSFER ->
                    "We have noted your concern about " + ref + ". " +
                            "Please do not share your PIN or OTP with anyone. " +
                            "Our dispute team will review the case and contact you through official support channels.";
            case PAYMENT_FAILED ->
                    "We have noted that " + ref + " may have caused an unexpected balance deduction. " +
                            "Our payments team will review the case and any eligible amount will be returned through official channels. " +
                            "Please do not share your PIN or OTP with anyone.";
            case DUPLICATE_PAYMENT ->
                    "We have noted the possible duplicate payment for " + ref + ". " +
                            "Our payments team will verify with the biller and any eligible amount will be returned through official channels. " +
                            "Please do not share your PIN or OTP with anyone.";
            case MERCHANT_SETTLEMENT_DELAY ->
                    "We have noted your concern about settlement " + ref + ". " +
                            "Our merchant operations team will check the batch status and update you on the expected settlement time through official channels.";
            case AGENT_CASH_IN_ISSUE ->
                    "We have noted your concern about " + ref + ". " +
                            "Our agent operations team will verify the status and resolve it through official channels. " +
                            "Please do not share your PIN or OTP with anyone.";
            case REFUND_REQUEST ->
                    "Thank you for reaching out. Refunds for completed transactions depend on the merchant's own policy. " +
                            "We recommend contacting the merchant directly for a refund. " +
                            "If you need help reaching them, please reply and we will guide you. " +
                            "Please do not share your PIN or OTP with anyone.";
            default ->
                    "Thank you for reaching out. To help you faster, please share the transaction ID, " +
                            "the amount involved, and a short description of what went wrong. " +
                            "Please do not share your PIN or OTP with anyone.";
        };
    }

    private List<String> buildReasonCodes(CaseType caseType, EvidenceVerdict verdict,
                                          TransactionMatch match) {
        List<String> codes = new ArrayList<>();
        codes.add(caseType.getValue());
        codes.add(verdict.getValue());
        if (match.transactionId != null) codes.add("transaction_match");
        if (match.isAmbiguous) codes.add("ambiguous_match");
        if (match.isDuplicate) codes.add("duplicate_detected");
        return codes;
    }

    private double computeConfidence(CaseType caseType, EvidenceVerdict verdict,
                                     TransactionMatch match, List<TransactionEntry> history) {
        if (caseType == CaseType.PHISHING_OR_SOCIAL_ENGINEERING) return 0.95;
        if (verdict == EvidenceVerdict.CONSISTENT && match.transactionId != null) return 0.88;
        if (verdict == EvidenceVerdict.INCONSISTENT) return 0.75;
        if (verdict == EvidenceVerdict.INSUFFICIENT_DATA && match.isAmbiguous) return 0.60;
        if (history.isEmpty()) return 0.70;
        return 0.65;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private boolean detectBangla(String text) {
        // Unicode range for Bengali script: U+0980–U+09FF
        return text != null && text.chars().anyMatch(c -> c >= 0x0980 && c <= 0x09FF);
    }

    private long extractMaxAmount(String lower) {
        long max = 0;
        Matcher m = AMOUNT_PATTERN.matcher(lower);
        while (m.find()) {
            try {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                if (val > max && val < 10_000_000) max = val; // sanity cap
            } catch (NumberFormatException ignored) {}
        }
        return max;
    }

    private boolean hasDuplicateInHistory(List<TransactionEntry> history) {
        if (history == null || history.size() < 2) return false;
        for (int i = 0; i < history.size(); i++) {
            for (int j = i + 1; j < history.size(); j++) {
                TransactionEntry a = history.get(i);
                TransactionEntry b = history.get(j);
                if (a.getAmount() != null && b.getAmount() != null
                        && Math.round(a.getAmount()) == Math.round(b.getAmount())
                        && Objects.equals(a.getCounterparty(), b.getCounterparty())
                        && Objects.equals(a.getType(), b.getType())
                        && timeDiffSeconds(a.getTimestamp(), b.getTimestamp()) <= 120) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<String> findDuplicateTransactionId(List<TransactionEntry> history) {
        if (history == null || history.size() < 2) return Optional.empty();
        for (int i = 0; i < history.size(); i++) {
            for (int j = i + 1; j < history.size(); j++) {
                TransactionEntry a = history.get(i);
                TransactionEntry b = history.get(j);
                if (a.getAmount() != null && b.getAmount() != null
                        && Math.round(a.getAmount()) == Math.round(b.getAmount())
                        && Objects.equals(a.getCounterparty(), b.getCounterparty())
                        && Objects.equals(a.getType(), b.getType())
                        && timeDiffSeconds(a.getTimestamp(), b.getTimestamp()) <= 120) {
                    // Return the second (later) transaction as the duplicate
                    return Optional.of(b.getTransactionId());
                }
            }
        }
        return Optional.empty();
    }

    private long timeDiffSeconds(String ts1, String ts2) {
        try {
            java.time.Instant i1 = java.time.Instant.parse(ts1);
            java.time.Instant i2 = java.time.Instant.parse(ts2);
            return Math.abs(java.time.Duration.between(i1, i2).getSeconds());
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private boolean historyHasType(List<TransactionEntry> history, String type) {
        return history != null && history.stream()
                .anyMatch(t -> type.equalsIgnoreCase(t.getType()));
    }

    private AnalyzeTicketResponse buildFallbackResponse(String ticketId) {
        return AnalyzeTicketResponse.builder()
                .ticketId(ticketId)
                .relevantTransactionId(null)
                .evidenceVerdict(EvidenceVerdict.INSUFFICIENT_DATA)
                .caseType(CaseType.OTHER)
                .severity(Severity.LOW)
                .department(Department.CUSTOMER_SUPPORT)
                .agentSummary("Unable to process ticket automatically. Manual review required.")
                .recommendedNextAction("Assign to a human agent for manual investigation.")
                .customerReply("Thank you for reaching out. Our team will review your case and get back to you through official channels. Please do not share your PIN or OTP with anyone.")
                .humanReviewRequired(true)
                .confidence(0.0)
                .reasonCodes(List.of("rule_engine_error", "fallback_response"))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner record for transaction match result
    // ─────────────────────────────────────────────────────────────────────────

    private static class TransactionMatch {
        final String transactionId;
        final long amount;
        final boolean isDuplicate;
        final boolean isAmbiguous;

        TransactionMatch(String transactionId, long amount, boolean isDuplicate) {
            this.transactionId = transactionId;
            this.amount = amount;
            this.isDuplicate = isDuplicate;
            this.isAmbiguous = false;
        }

        private TransactionMatch(String transactionId, long amount, boolean isDuplicate, boolean isAmbiguous) {
            this.transactionId = transactionId;
            this.amount = amount;
            this.isDuplicate = isDuplicate;
            this.isAmbiguous = isAmbiguous;
        }

        static TransactionMatch none() {
            return new TransactionMatch(null, 0, false, false);
        }

        static TransactionMatch ambiguous() {
            return new TransactionMatch(null, 0, false, true);
        }
    }
}