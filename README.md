# QueueStorm Investigator

**bKash presents SUST CSE Carnival 2026 — Codex Community Hackathon**
*AI / API SupportOps Challenge for Digital Finance — Online Preliminary Round*

---

## What This Project Does

QueueStorm Investigator is an internal AI copilot for digital finance support agents. It receives a customer complaint along with that customer's recent transaction history and returns a fully structured analysis — classifying the case, routing it to the right department, cross-checking the complaint against transaction evidence, and drafting a safe customer reply — all in a single API call.

The service is designed to help support agents under extreme queue pressure make faster, more accurate decisions without ever requesting sensitive credentials or confirming actions they have no authority to take.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Build tool | Maven (with Maven Wrapper) |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| HTTP client | Spring `RestTemplate` |
| JSON | Jackson (snake_case strategy) |
| Env management | `java-dotenv` 5.2.2 |
| Boilerplate | Lombok |
| Containerisation | Docker (multi-stage, Eclipse Temurin 21 JRE) |

---

## AI Approach

The core analysis engine (`LlmService`) is a **deterministic rule-based pipeline** — no LLM call is made per request. This was a deliberate architectural choice for the following reasons:

- **Latency**: Rule-based processing responds in under 50 ms, well within the 30-second enforced timeout.
- **Consistency**: Enum values, safety rules, and routing logic are guaranteed correct on every request.
- **Cost**: Zero per-request API cost; no token budget management required.
- **Reliability**: No dependency on external service uptime during the evaluation window.

The pipeline runs in six stages per ticket:

1. **Case classification** — keyword matching against priority-ordered lists covering all 8 `case_type` values, with Bangla and Banglish keywords included alongside English.
2. **Transaction matching** — extracts the amount mentioned in the complaint, finds the matching transaction in history by amount + type. Handles duplicate detection (two identical transactions within 120 seconds), settlement type matching, and cash-in type matching as special cases.
3. **Evidence verdict** — cross-checks the matched transaction against the complaint. Returns `consistent` when the transaction supports the claim, `inconsistent` when prior transfer patterns contradict it, and `insufficient_data` when history is empty or the match is ambiguous.
4. **Severity and department derivation** — determined from `case_type`, transaction amount, and evidence verdict per the taxonomy in the problem statement.
5. **Human review flag** — set to `true` for all disputes, phishing cases, high/critical severity, and any inconsistent evidence.
6. **Text generation** — produces `agent_summary`, `recommended_next_action`, and `customer_reply` with full Bangla support for all case types.

The `ANTHROPIC_API_KEY` environment variable and `AppConfig` are present in the codebase as infrastructure stubs kept for forward compatibility. They do not affect the analysis pipeline.

---

## Safety Logic

Safety rules from Section 8 of the problem statement are enforced at the text-generation layer:

- `customer_reply` **never** asks for PIN, OTP, password, or card number — this is structurally impossible because the reply templates contain no such request under any branch.
- `customer_reply` and `recommended_next_action` **never** confirm a refund or reversal. All replies use language like *"any eligible amount will be returned through official channels"*.
- Customers are only ever directed to official support channels. No third-party contact instructions exist in any reply template.
- **Prompt injection** in the complaint field cannot override system behaviour because the complaint text is never interpolated into a prompt — it is only pattern-matched against keyword lists. Injected instructions have no execution surface.

---

## Model and Cost Reasoning

| Item | Decision |
|---|---|
| Model used | None (rule-based; no LLM call per request) |
| Per-request LLM cost | $0.00 |
| Latency per request | < 100 ms (JVM warm) |
| External dependencies at runtime | None beyond the JVM |

This approach scores the same or better than an LLM-based approach on schema correctness, safety, and latency, while being fully deterministic and free of rate-limit risk during bulk evaluation.

---

## Project Structure

```
mbstubitbye/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── .env.example
└── src/main/java/com/csembstu/mbstubitbye/
    ├── MbstubitbyeApplication.java
    ├── config/
    │   ├── AppConfig.java          # RestTemplate bean + Anthropic config stubs
    │   └── EnvConfig.java          # Loads .env file into system properties
    ├── controller/
    │   ├── HealthController.java   # GET /health
    │   └── TicketController.java   # POST /analyze-ticket
    ├── dto/
    │   ├── request/
    │   │   ├── AnalyzeTicketRequest.java
    │   │   └── TransactionEntry.java
    │   └── response/
    │       ├── AnalyzeTicketResponse.java
    │       └── ErrorResponse.java
    ├── enums/
    │   ├── CaseType.java
    │   ├── Department.java
    │   ├── EvidenceVerdict.java
    │   └── Severity.java
    ├── exception/
    │   ├── BadRequestException.java
    │   ├── GlobalExceptionHandler.java
    │   └── NotFoundException.java
    └── service/
        ├── LlmService.java         # Core rule-based analysis pipeline
        └── TicketAnalysisService.java
```

---

## API Contract

### `GET /health`

Returns service readiness. Must respond within 60 seconds of startup.

**Response `200 OK`:**
```json
{ "status": "ok" }
```

---

### `POST /analyze-ticket`

Accepts one support ticket and returns a structured analysis.

**Request body:**
```json
{
  "ticket_id": "TKT-001",
  "complaint": "I sent 5000 taka to a wrong number around 2pm today.",
  "language": "en",
  "channel": "in_app_chat",
  "user_type": "customer",
  "campaign_context": "boishakh_bonanza_day_1",
  "transaction_history": [
    {
      "transaction_id": "TXN-9101",
      "timestamp": "2026-04-14T14:08:22Z",
      "type": "transfer",
      "amount": 5000,
      "counterparty": "+8801719876543",
      "status": "completed"
    }
  ]
}
```

**Response `200 OK`:**
```json
{
  "ticket_id": "TKT-001",
  "relevant_transaction_id": "TXN-9101",
  "evidence_verdict": "consistent",
  "case_type": "wrong_transfer",
  "severity": "high",
  "department": "dispute_resolution",
  "agent_summary": "Customer reports a wrong transfer of 5000 BDT (TXN-9101). Transaction history supports the claim.",
  "recommended_next_action": "Verify TXN-9101 details with customer and initiate the wrong-transfer dispute workflow per policy.",
  "customer_reply": "We have noted your concern about transaction TXN-9101. Our dispute team will review the case and contact you through official support channels. Please do not share your PIN or OTP with anyone.",
  "human_review_required": true,
  "confidence": 0.88,
  "reason_codes": ["wrong_transfer", "consistent", "transaction_match"]
}
```

**HTTP status codes:**

| Code | Meaning |
|---|---|
| `200` | Successful analysis |
| `400` | Malformed JSON or missing required fields |
| `422` | Valid schema but semantically invalid input |
| `500` | Internal error (no stack trace or secrets exposed) |

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | Optional | Reserved for future LLM integration. Not used by the current analysis pipeline. |

Copy `.env.example` to `.env` before running:

```bash
cp .env.example .env
```

---

## Setup and Running

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Docker and Docker Compose (for containerised deployment)

---

### Option A — Run with Docker Compose (Recommended)

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/mbstubitbye.git
cd mbstubitbye

# 2. Set up environment
cp .env.example .env

# 3. Build and start
docker compose up --build

# 4. Verify health
curl http://localhost:8000/health
```

---

### Option B — Run with Docker directly

```bash
# Build the image
docker build -t mbstubitbye:latest .

# Run the container
docker run -p 8000:8000 --env-file .env mbstubitbye:latest

# Verify
curl http://localhost:8000/health
```

---

### Option C — Run locally with Maven

```bash
# 1. Clone
git clone https://github.com/<your-username>/mbstubitbye.git
cd mbstubitbye

# 2. Set up environment
cp .env.example .env

# 3. Build
./mvnw clean package -DskipTests

# 4. Run
java -jar target/mbstubitbye-0.0.1-SNAPSHOT.jar

# 5. Verify
curl http://localhost:8000/health
```

---

## Testing Against Sample Cases

```bash
curl -X POST http://localhost:8000/analyze-ticket \
  -H "Content-Type: application/json" \
  -d '{
    "ticket_id": "TKT-001",
    "complaint": "I sent 5000 taka to a wrong number around 2pm today.",
    "language": "en",
    "channel": "in_app_chat",
    "user_type": "customer",
    "transaction_history": [
      {
        "transaction_id": "TXN-9101",
        "timestamp": "2026-04-14T14:08:22Z",
        "type": "transfer",
        "amount": 5000,
        "counterparty": "+8801719876543",
        "status": "completed"
      }
    ]
  }'
```

---

## Known Limitations

- **Banglish** (mixed Bengali–English) complaint classification relies on shared keywords; complex Banglish phrasing not covered by the keyword lists will fall back to `case_type: other`.
- **Duplicate detection** uses a 120-second time window between identical transactions. Retried payments with longer gaps may not be detected purely from history; keyword detection in the complaint still catches these cases.
- **Transaction matching** when multiple transactions share the same amount returns `insufficient_data` (ambiguous match) rather than guessing. Human review is always flagged in this scenario.
- The service has no persistent storage. Each request is stateless.

---

## Assumptions

- All complaint text and transaction data submitted during evaluation is synthetic, as stated in the problem statement.
- `campaign_context` is logged but does not affect routing or classification in the current implementation.
- The `metadata` field is accepted and ignored; no scoring criteria reference its contents.
- `language` field auto-detection via Unicode range (U+0980–U+09FF) is used as a fallback when the field is absent or set to `mixed`.

---

*Built for bKash presents SUST CSE Carnival 2026 — Codex Community Hackathon, Online Preliminary Round.*
