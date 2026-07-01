# DataKite Async Categorization Ledger

A full-stack, event-driven ledger. Transactions are ingested asynchronously over JMS,
validated for fraud, categorized by an AI/NLP engine, and displayed on a dashboard.

> Status: feature-complete. Backend (ingest → JMS → fraud validation → categorization →
> persistence → ledger feed + category analytics) and frontend (live dashboard polling
> both endpoints) are implemented and verified end-to-end, including in a real browser.
> See `AI_LOG.md` for the development log.

## Architecture

```
Client → POST /api/v1/transactions → 202 Accepted
                                    → publish to JMS queue (transactions.ingest)
                                                      ↓
                                    TransactionListener (async consumer)
                                         → FraudValidationService  (> $5,000 → PENDING_REVIEW)
                                         → CategorizationService   (AI/NLP category)
                                         → persist to PostgreSQL
                                                      ↓
Client ← GET /api/v1/transactions ← ledger feed
Client ← GET /api/v1/transactions/analytics/by-category ← chart data
```

## Tech stack

- **Backend**: Java 17, Spring Boot 3.3, Spring Data JPA, Flyway, PostgreSQL
- **AI categorization**: Spring AI 1.0.5 (OpenAI-compatible client) → [Groq](https://groq.com)
  (`openai/gpt-oss-20b`), with a rule-based fallback — see Business Rules below
- **Messaging**: ActiveMQ Artemis, embedded in-process (no separate broker to run/deploy —
  it starts and stops with the Spring Boot application). Configured with an explicit
  retry/DLQ policy — see "Reliability" below.
- **Frontend**: Next.js (App Router), React, shadcn/ui, Tailwind CSS, Recharts
- **Infra**: Docker Compose (PostgreSQL, backend, frontend)

## Project structure

```
backend/    Spring Boot service (REST API, JMS producer/consumer, categorization, persistence)
frontend/   Next.js dashboard (ledger table + category analytics chart)
```

## Running with Docker Compose

Requires Docker and Docker Compose.

```bash
docker compose up --build
```

This starts:

| Service   | URL                          |
|-----------|------------------------------|
| Frontend  | http://localhost:3000        |
| Backend   | http://localhost:8080        |
| Postgres  | localhost:5432               |

Artemis (the JMS broker) runs embedded inside the `backend` service's JVM — there is no
separate broker container or port to connect to.

### Makefile shortcuts

A `Makefile` wraps the common Docker Compose operations, plus a couple of composite
commands for recovering from a stuck environment (e.g. leftover containers or a local
process still holding a port):

```bash
make            # show all available targets
make up         # docker compose up --build
make down       # docker compose down (keeps the DB volume)
make kill-ports # force-free ports 3000/8080/5432 — stops this project's containers
                # AND kills any stray local process still bound to those ports
make clean-db   # stop the stack and wipe the Postgres volume (destructive)
make buildRun       # kill-ports, then rebuild and start — keeps existing DB data
make freshBuildRun  # kill-ports + clean-db, then rebuild and start — fresh, empty DB
```

`kill-ports` is the one to reach for if `docker compose up` fails with a port-already-in-use
error — it stops this project's own containers by name and also kills any leftover local
process (e.g. a `./mvnw spring-boot:run` or `npm run dev` you forgot was still running) on
ports 3000/8080/5432, without touching unrelated containers or processes.

## Database migrations

Migrations are managed by Flyway and run automatically on backend startup, from
`backend/src/main/resources/db/migration`. `V1__init_transactions_table.sql` creates the
`transactions` table.

## Running locally (without Docker)

### Backend

Requires JDK 17+, Maven, and a running PostgreSQL instance (Artemis needs nothing extra —
it runs embedded in the application process).

```bash
cd backend
./mvnw spring-boot:run
```

By default this connects to `jdbc:postgresql://localhost:5432/datakite_ledger` (user/pass
`datakite`/`datakite`, matching the Docker Compose Postgres service). Point it at a
different database by overriding the standard Spring env vars, e.g.:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/datakite_ledger \
SPRING_DATASOURCE_USERNAME=datakite \
SPRING_DATASOURCE_PASSWORD=datakite \
./mvnw spring-boot:run
```

Full configuration lives in `src/main/resources/application.properties`.

**CORS**: the backend only allows requests from `FRONTEND_ORIGIN` (default
`http://localhost:3000`) on `/api/**`. If you serve the frontend from a different origin,
set `FRONTEND_ORIGIN` accordingly, e.g. `FRONTEND_ORIGIN=http://localhost:4000 ./mvnw
spring-boot:run`.

**AI categorization (Groq)**: set `GROQ_API_KEY` to a real key from
[console.groq.com](https://console.groq.com) to enable real LLM categorization:

```bash
GROQ_API_KEY=gsk_your_key_here ./mvnw spring-boot:run
```

Without a key (or with the placeholder default), the app still starts and works
normally — every categorization call falls back to the rule-based keyword matcher. See
"Categorization engine" under Business Rules below for the full fallback design.

### Backend tests

```bash
cd backend
./mvnw test
```

Unit tests only — no Docker/database/broker/API key required. Covers
`FraudValidationService` (the $5,000 boundary), `RuleBasedCategorizationService` (one case
per category plus the Miscellaneous fallback), `CategorizationService` (LLM response
parsing and its fallback to rule-based matching, with the LLM call mocked via Mockito deep
stubs), and `TransactionController` (via a `@WebMvcTest` MockMvc slice test, with
`TransactionService` mocked out — checks the `202`/`400` HTTP contract, the structured
error response shape, and that the controller delegates correctly). The JMS retry/DLQ
policy isn't unit-tested (it's broker behavior, not testable without a running Artemis
instance) — it was verified live instead; see `AI_LOG.md`.

### Manual end-to-end testing

With the stack running (Docker Compose or backend + frontend run separately),
`scripts/test-requests.sh` has ready-to-run `curl` requests covering the fraud rule (one
over the $5,000 threshold, one exactly at the boundary), all five categories (one
transaction per category, including the Miscellaneous fallback), an invalid payload, and
both `GET` endpoints:

```bash
./scripts/test-requests.sh
```

Copy/paste individual blocks from the script directly into a terminal if you just want to
run one case.

### Frontend

Requires Node.js 20+.

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
```

Open http://localhost:3000 — the dashboard fetches the ledger feed and category
analytics on load and polls the backend every 5 seconds, so newly ingested transactions
appear without a manual refresh. UI components are pre-generated shadcn/ui primitives
(`src/components/ui/`) — to add more, run `npx shadcn@latest add <component>`.

The ledger feed table has client-side filters — date range, amount range, category, and
status — applied to whatever's currently loaded (filtering doesn't hit the API; it's a
`useMemo` over the fetched list in `LedgerTable`). The layout is responsive: the filter
grid reflows from 4 columns down to 1 on narrow screens, and the table scrolls
horizontally on mobile rather than squeezing its columns (with a "swipe to see more" hint
below `sm`).

## Reliability

**JMS retry/DLQ**: if `TransactionListener` fails to persist a transaction (e.g. a
transient DB outage), the message is redelivered with exponential backoff rather than
lost or retried forever:

| Property | Default | Meaning |
|---|---|---|
| `datakite.ledger.jms.max-delivery-attempts` | `3` | total attempts before giving up |
| `datakite.ledger.jms.redelivery-delay-ms` | `1000` | delay before the 2nd attempt |
| `datakite.ledger.jms.max-redelivery-delay-ms` | `10000` | cap on the backoff delay (doubles each attempt) |
| `datakite.ledger.jms.dead-letter-address` | `DLQ` | where the message goes after exhausting retries |

After `max-delivery-attempts` failures, Artemis routes the message to the dead-letter
address instead of redelivering it again — the transaction is not silently dropped, but
it also won't be retried indefinitely. Verified live by actually stopping Postgres
mid-flight and watching the attempt count, backoff timing, and DLQ routing happen for
real (see `AI_LOG.md`).

## API

| Method | Path                                          | Description                                  |
|--------|------------------------------------------------|-----------------------------------------------|
| POST   | `/api/v1/transactions`                          | Ingest a transaction; returns `202 Accepted`  |
| GET    | `/api/v1/transactions`                          | List transactions for the ledger feed         |
| GET    | `/api/v1/transactions/analytics/by-category`    | Totals grouped by AI-predicted category       |

### Ingest request body

```json
{
  "amount": 129.99,
  "currency": "USD",
  "date": "2026-07-01T12:00:00Z",
  "description": "Subscription fee for AWS Cloud us-east-1"
}
```

### Validation error response (`400 Bad Request`)

Invalid ingest payloads return a structured error body rather than a raw exception
message, with one entry per invalid field:

```json
{
  "timestamp": "2026-07-01T22:50:24.780257678+03:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": [
    { "field": "amount", "message": "must be greater than 0" },
    { "field": "currency", "message": "must not be blank" },
    { "field": "description", "message": "must not be blank" },
    { "field": "date", "message": "must not be null" }
  ]
}
```

### Ledger feed response (`GET /api/v1/transactions`)

Newest first (ordered by `createdAt` descending):

```json
[
  {
    "id": "6b2f5cbf-3b08-44c4-8db8-bf28c4b3a50c",
    "amount": 129.99,
    "currency": "USD",
    "transactionDate": "2026-07-01T12:00:00Z",
    "description": "Subscription fee for AWS Cloud us-east-1",
    "status": "CATEGORIZED",
    "category": "INFRASTRUCTURE"
  }
]
```

### Category analytics response (`GET /api/v1/transactions/analytics/by-category`)

```json
[
  { "category": "INFRASTRUCTURE", "totalAmountUsd": 129.99, "transactionCount": 1 }
]
```

`totalAmountUsd` is a raw sum of `amount` for now — see the single-currency v1
simplification noted below.

## Business rules

- **Fraud prevention**: any transaction with `amount > $5,000` has its persisted `status`
  overridden to `PENDING_REVIEW` instead of `CATEGORIZED`. The category is still computed
  and persisted alongside it (the rule overrides the *status*, not the categorization
  itself) — this flags the transaction for human review without discarding the AI's
  categorization work.
- **Categorization engine**: `CategorizationService` calls an LLM (`openai/gpt-oss-20b` via
  [Groq](https://groq.com)'s OpenAI-compatible API) to classify the description, and falls
  back to `RuleBasedCategorizationService` (keyword matching, first match wins) on *any*
  failure — no `GROQ_API_KEY` configured, network error, rate limit, or an unparseable
  response. This means a Groq outage (or simply not having a key) degrades gracefully
  rather than blocking transaction processing. See `AI_LOG.md` for the design rationale
  and a real bug found/fixed while wiring this up (a doubled `/v1/v1/...` URL path).
- **Categories**: `SaaS/Software`, `Infrastructure`, `Business Meals`, `Operations`,
  `Miscellaneous`.
- **Currency (v1 simplification)**: category analytics sums raw `amount` values with no
  FX conversion — i.e. it assumes a single currency. Multi-currency support (real "USD
  equivalent" conversion) is a known gap, not yet implemented.
