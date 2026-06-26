# Team MBSTU_BitBye

>bKash presents SUST CSE Carnival 2026 — Codex Community Hackathon

---

## Public Endpoint Base URL

> https://mbstubitbye.onrender.com

| Endpoint | Method | Description |
|---|---|---|
| https://mbstubitbye.onrender.com/health | `GET` | Health check |
| https://mbstubitbye.onrender.com/analyze-ticket | `POST` | Analyze support ticket |


---

## What This Project Does

QueueStorm Investigator is a copilot that analyzes finance complaints, verifies transactions, routes cases, and drafts replies in one API call. It helps support agents resolve issues faster while maintaining security and compliance.


---

## Tech Stack

| Component | Technologies |
| --- | --- |
| **Backend** | Java 21 (Temurin), Spring Boot 3.5.10 |
| **Data Access** | Spring Data JPA (Hibernate) |
| **Database** | PostgreSQL 15 |
| **Containerization** | Docker, Docker Compose |
| **Deployment** | Render (Managed Cloud) |

---


## Project Structure

```
MBSTUBITBYE/
├── .github/                                  
├── .mvn/                                       # Maven Wrapper config
├── src/
│   ├── main/
│   │   ├── java/com/csembstu/mbstubitbye/
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java              # RestTemplate bean + Anthropic config stubs
│   │   │   │   └── EnvConfig.java              # Loads .env file into system properties
│   │   │   ├── controller/
│   │   │   │   ├── HealthController.java       # GET /health
│   │   │   │   └── TicketController.java       # POST /analyze-ticket
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── AnalyzeTicketRequest.java
│   │   │   │   │   └── TransactionEntry.java
│   │   │   │   └── response/
│   │   │   │       ├── AnalyzeTicketResponse.java
│   │   │   │       └── ErrorResponse.java
│   │   │   ├── enums/
│   │   │   │   ├── CaseType.java
│   │   │   │   ├── Department.java
│   │   │   │   ├── EvidenceVerdict.java
│   │   │   │   └── Severity.java
│   │   │   ├── exception/
│   │   │   │   ├── BadRequestException.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   └── NotFoundException.java
│   │   │   ├── service/
│   │   │   │   ├── LlmService.java             # Core rule-based analysis pipeline
│   │   │   │   └── TicketAnalysisService.java
│   │   │   └── MbstubitbyeApplication.java     # Spring Boot entry point
│   │   └── resources/
│   │       └── application.yml                 # Server, Jackson, and logging config
│   └── test/
│       └── java/com/csembstu/mbstubitbye/
│           └── MbstubitbyeApplicationTests.java
├── .gitattributes
├── .gitignore
├── docker-compose.yml
├── Dockerfile
├── pom.xml

```

---


## Setup and Running

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Docker and Docker Compose (for containerised deployment)

---

### Option A — Run with Docker Compose

```bash
# 1. Clone the repository
git clone https://github.com/alamgir-ahosain/mbstubitbye.git
cd mbstubitbye

# 2. Build and start
docker compose up --build

# 3. Verify health
curl http://localhost:8000/health
```

---

### Option B — Run with Docker directly

```bash
# Build the image
docker build -t mbstubitbye:latest .

# Run the container
docker run -p 8000:8000 mbstubitbye:latest

# Verify
curl http://localhost:8000/health
```

---

### Option C — Run locally with Maven

```bash
# 1. Clone
git clone https://github.com/alamgir-ahosain/mbstubitbye.git
cd mbstubitbye

# 2. Build
./mvnw clean package -DskipTests

# 3. Run
java -jar target/mbstubitbye-0.0.1-SNAPSHOT.jar

# 4. Verify
curl http://localhost:8000/health
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

---

####  Sample Request & Response — Payment Failed

**Request:**

```json
{
  "ticket_id": "TKT-003",
  "complaint": "I tried to pay 1200 taka for my mobile recharge but the app showed failed. But my balance was deducted! Please refund my money.",
  "language": "en",
  "channel": "in_app_chat",
  "user_type": "customer",
  "transaction_history": [
    {
      "transaction_id": "TXN-9301",
      "timestamp": "2026-04-14T16:00:00Z",
      "type": "payment",
      "amount": 1200,
      "counterparty": "MERCHANT-MOBILE-OP",
      "status": "failed"
    }
  ]
}
```

**Response `200 OK`:**

```json
{
  "ticket_id": "TKT-003",
  "relevant_transaction_id": "TXN-9301",
  "evidence_verdict": "consistent",
  "case_type": "payment_failed",
  "severity": "high",
  "department": "payments_ops",
  "agent_summary": "Customer reports a failed payment of 1200 BDT (TXN-9301) where balance may have been deducted. Requires payments ops investigation.",
  "recommended_next_action": "Investigate TXN-9301 ledger status. If balance was deducted on a failed payment, initiate the automatic reversal flow within standard SLA.",
  "customer_reply": "We have noted that transaction TXN-9301 may have caused an unexpected balance deduction. Our payments team will review the case and any eligible amount will be returned through official channels. Please do not share your PIN or OTP with anyone.",
  "human_review_required": true,
  "confidence": 0.88,
  "reason_codes": ["payment_failed", "consistent", "transaction_match"]
}
```

---

####  Sample Request & Response — Wrong Transfer

**Request:**

```json
{
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

---

####  Validation Error — Missing `ticket_id`


**Request:**
```json
{
  "ticket_id": "",
  "complaint": "I tried to pay 1200 taka but balance was deducted.",
  "language": "en",
  "channel": "in_app_chat",
  "user_type": "customer"
}
```

**Response `400 Bad Request`:**
```json
{
  "status": 400,
  "error": "Validation failed",
  "message": "ticket_id is required and must not be blank"
}
```

---

####  Validation Error — Missing `user_type`

**Request:**

```json
{
  "ticket_id": "TKT-003",
  "complaint": "I tried to pay 1200 taka but balance was deducted.",
  "language": "en",
  "channel": "in_app_chat"
}
```

**Response `400 Bad Request`:**

```json
{
  "status": 400,
  "error": "Validation failed",
  "message": "userType is required and must not be blank"
}
```

---

####  Validation Error — Missing `amount` in transaction

**Request:**

```json
{
  "ticket_id": "TKT-003",
  "complaint": "I tried to pay 1200 taka but balance was deducted.",
  "language": "en",
  "channel": "in_app_chat",
  "user_type": "customer",
  "transaction_history": [
    {
      "transaction_id": "TXN-9301",
      "timestamp": "2026-04-14T16:00:00Z",
      "type": "payment",
      "counterparty": "MERCHANT-MOBILE-OP",
      "status": "failed"
    }
  ]
}
```

**Response `400 Bad Request`:**

```json
{
  "status": 400,
  "error": "Validation failed",
  "message": "amount is required in each transaction entry"
}
```

---

## HTTP Status Codes

| Code | Meaning |
|---|---|
| `200` | Successful analysis |
| `400` | Malformed JSON or missing required fields |
| `422` | Valid schema but semantically invalid input |
| `500` | Internal error (no stack trace or secrets exposed) |

---

## Team MBSTU_BitBye

* **Alamgir Hosain**
* **Sakib Shehan**
* **Ariful Islam**
