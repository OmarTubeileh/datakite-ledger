# AI Log

Audit trail of AI-assisted development for the DataKite Async Categorization Ledger.

- **Start time**: 01/07/2026, 6:00PM
- **Finish time**: 02/07/2026, 12:00AM

## 1. AI tooling stack

- **Claude Code** (model: Claude Sonnet 5) — project scaffolding, backend implementation,
  architecture decisions, verification, and this documentation. Used interactively via the
  CLI, directing it turn-by-turn rather than accepting single-shot generations.
- **Ruflo (claude-flow) MCP plugin** — installed and configured in Claude Code (see the
  project's own `CLAUDE.md`, which is largely ruflo's auto-generated configuration) to make
  multi-agent orchestration, persistent memory, and hook-based coordination tooling
  available for larger tasks. In practice, this project's actual implementation work
  (backend code, frontend code, tests, docs) was done through Claude Code's native
  file/bash tools directly, one reviewed step at a time, rather than by delegating to
  ruflo's swarm/agent-spawning features — the plugin was available as part of the standing
  toolchain rather than something this particular build relied on end to end.

## 2. Prompt engineering strategy

**Constructing the JMS asynchronous queues:**

The queue wiring wasn't built from one large prompt — it followed a plan-then-execute
strategy: first asked for an implementation plan with explicit tool options ("Give me a
before you start implementing the project, give me the options for the tools you intend to
use for asynchronous queues, database, etc..."), which surfaced a real fork the assessment 
leaves open — containerized Artemis vs. **embedded in-process Artemis** vs. plain Spring 
`ApplicationEventPublisher` — with tradeoffs for each. Picked embedded Artemis explicitly 
before any code was written. Only then was the short instruction "start on phase 1" enough
to get correct results, because the architecture decision (embedded broker, queue name, JSON
message conversion) had already been pinned down rather than left for the model to guess mid-implementation.
Concretely, this drove: `JmsConfig` (a `MappingJackson2MessageConverter` reusing Spring's
autoconfigured `ObjectMapper` — needed for `OffsetDateTime` fields to serialize correctly),
`TransactionProducer`, and `TransactionListener`, plus promoting
`artemis-jakarta-server` from a test-scoped to a main dependency and switching
`spring.artemis.mode` to `embedded` in `application.yml`.

**Building the React frontend:**

Same plan-then-execute pattern as the JMS work: the frontend architecture (shadcn/ui +
Tailwind + Recharts, polling for "real-time") had already been decided during the initial
scaffold and the Phase 5 planning discussion, so "continue with Phase 5" was sufficient to
drive the actual implementation. Concretely this meant: running the real `shadcn` CLI
(`npx shadcn@latest add table card badge button chart`) to generate genuine component
source rather than hand-rolling lookalikes, completing the shadcn CSS-variable theme setup
in `globals.css`/`tailwind.config.ts` that the scaffold phase had left incomplete (the
stub components referenced `bg-card`/`text-muted-foreground`/etc. classes that didn't
actually resolve to anything without the theme tokens), wiring `lib/api.ts` to the real
backend endpoints, and building a `"use client"` `Dashboard` component that fetches once on
mount and polls every 5 seconds — the simplest option that satisfies the PDF's "real-time
ledger feed" requirement without adding a WebSocket layer the assessment doesn't ask for.

**Iterating on the frontend after the initial build:** once the base dashboard existed, I
asked for two specific follow-up features rather than a vague "improve the frontend" —
*"add filters to the Ledger Feed, add Date Range, Amount Range, Category, Status filters,
make sure the page follows Responsive Web Design."* Naming the exact filter dimensions and
explicitly calling out responsive design as a requirement (not an afterthought) meant the
implementation covered both in one pass — including verifying actual breakpoint behavior
in a real browser at multiple viewport widths, not just adding Tailwind classes and
assuming they worked.

## 3. Human-in-the-loop validation

No qualifying incident yet from Phase 1 (JMS wiring), Phase 2 (fraud validation +
categorization + persistence), or Phase 3 (ledger feed + category analytics endpoints) —
all three were verified correct on the first real end-to-end run rather than stopping at
"it compiles":
- Phase 1: booted the app, POSTed a transaction, confirmed `202` plus the message actually
  being consumed off the embedded Artemis queue in the application logs.
- Phase 2: booted the app against a real Postgres and POSTed three transactions designed
  to exercise the edge cases — a normal one, one over the $5,000 fraud threshold, and one
  at exactly $5,000 (the boundary, which per the PDF's "exceeds" wording should *not* be
  flagged) — then queried the database directly to confirm `status`/`category` landed
  correctly for all three rather than trusting the code by inspection alone.
- Phase 3: this one specifically deserved verification rather than trust-by-inspection,
  because `TransactionRepository.findCategoryTotals()` uses a Spring Data JPA
  **interface-based projection** on a custom `@Query` with a `GROUP BY` — a pattern that
  compiles cleanly even when the alias-to-getter binding is wrong, and only fails (or
  silently returns nulls) at runtime. Booted the app, POSTed four transactions across
  three categories, and hit both `GET` endpoints for real: confirmed the ledger feed came
  back newest-first with the right `status`/`category` per row, and the analytics endpoint
  correctly grouped and summed per category. It worked correctly on the first run, but the
  point is this class of bug (right-looking code, wrong-at-runtime projection binding)
  would not have been caught by compilation or by reading the code — only by executing it.
- Ledger feed filters (date range, amount range, category, status) + a responsive-design
  pass: this one turned up a **process bug rather than a code bug**, worth logging for the
  same reason — verification only works if you verify the right thing. While setting up an
  "isolated" browser test against a fresh backend/Postgres, `curl` and Playwright checks
  kept returning data that didn't match what had just been seeded. Investigating (`ss
  -tlnp`, `docker ps -a`, reading the crashed backend's own log) showed `./mvnw
  spring-boot:run` had failed immediately with "Port 8080 was already in use" — every
  request in that window had actually been hitting the user's own already-running
  `docker compose` stack from an earlier session, not the intended isolated instance. Fixed
  by rerunning with an explicit non-default port (`SERVER_PORT`, a separate Postgres
  container, and the frontend on its own port) so verification couldn't silently collide
  with a live environment again. The filters themselves then verified correctly across
  desktop/tablet/mobile viewports with zero console errors, including confirming the
  table's horizontal scroll genuinely works on mobile rather than silently clipping
  content (checked `scrollWidth` vs. `clientWidth` and scrolled it programmatically, not
  just eyeballing a screenshot).

- Phase 4 (unit tests): wrote tests deliberately targeting the edge cases already probed
  manually in Phase 2/3 (the $5,000 boundary, one case per category, the Miscellaneous
  fallback, a `400` for an invalid ingest payload) so they're now regression-protected
  rather than only having been checked once by hand. All 14 tests passed on the first run
  (`./mvnw test`, no database/broker required since the controller test mocks
  `TransactionService` via `@WebMvcTest`).

**Phase 5 (frontend) produced a genuine bug — a CORS misconfiguration:**

What happened: after wiring the frontend to fetch from the backend and building
successfully (`tsc --noEmit` and `next build` both clean), I initially treated that as
sufficient verification — exactly the trap this log has been warning about in earlier
phases (a bug can be invisible to compilation/type-checking and only appear at runtime).
To actually check the UI worked, I installed a headless Chromium (Playwright) and drove
the real dashboard in a real browser against the real backend.

How it was identified: the ledger table rendered "No transactions yet." and the chart was
empty, despite the backend genuinely holding seeded transactions (confirmed separately via
`curl`). The browser's console errors, captured via Playwright, showed the actual cause
immediately:
```
Access to fetch at 'http://localhost:8080/api/v1/transactions' from origin
'http://localhost:3000' has been blocked by CORS policy: No
'Access-Control-Allow-Origin' header is present on the requested resource.
```
Spring Boot does not enable cross-origin requests by default, and nothing in the backend
had configured CORS — an omission that neither `curl` (no `Origin` header, so no
browser-enforced CORS check applies) nor a type-checked/built frontend would ever surface,
since the failure only happens inside an actual browser's fetch/XHR stack.

Debugging process: reproduced with `curl -v` first to rule out the backend being down
(it returned `202`/`200` fine — confirming the failure was browser-specific, not a server
outage), then correlated the Playwright console error directly to the missing
`Access-Control-Allow-Origin` response header.

The fix: added `com.datakite.ledger.config.WebConfig` (implements `WebMvcConfigurer`,
`addCorsMappings` on `/api/**`) allowing the configurable `datakite.ledger.cors.allowed-origin`
property (`FRONTEND_ORIGIN` env var, defaulting to `http://localhost:3000` to match both
local dev and the Docker Compose frontend port). Restarted the backend, re-ran the same
Playwright script against the same seeded data: zero console errors, all 3 rows rendered
with correct amounts/categories/statuses, and 3 bars rendered on the chart. Screenshot
confirmed the dashboard visually matches the data. Re-ran `./mvnw test` afterward to
confirm the new config class didn't regress the existing 14 unit tests (it didn't).

**Real LLM categorization (Groq) produced a genuine bug — a doubled API path:**

Before writing any code, I looked up current information rather than trusting training
data for two things that are exactly the kind of fact that goes stale: which LLM providers
currently offer a free API tier, and which Groq model to use. That check caught a real
problem before it became a bug — my first-instinct model choices, `llama-3.3-70b-versatile`
and `llama-3.1-8b-instant`, had been deprecated by Groq on **June 17, 2026**, about two
weeks before this session. Used `openai/gpt-oss-20b` instead, per Groq's current docs. Also
verified Spring AI's actual current version constraints (2.0 requires Spring Boot 4, which
this project isn't on) before picking Spring AI 1.0.5, the GA line that supports Boot 3.3+.

What happened: after implementing `CategorizationService` (LLM-primary, falling back to
the renamed `RuleBasedCategorizationService` on any failure) and getting a clean
`./mvnw test` pass with mocked LLM calls, I booted the real app (no real Groq key
available in this environment, so using the documented placeholder) and POSTed a real
transaction to exercise the actual network call — not just the mocked unit tests. The
fallback engaged and the transaction categorized correctly either way, which could easily
have looked like "it works" — but reading the actual log line, rather than just checking
the end result, showed the LLM call had failed with an unexpected error:
```
HTTP 404 - {"error":{"message":"Unknown request URL: POST /openai/v1/v1/chat/completions.
...
```

How it was identified: the doubled `/v1/v1/` segment in the URL was the giveaway. Groq's
own documentation shows the base URL as `https://api.groq.com/openai/v1` (matching the raw
OpenAI SDK convention, where you supply the full versioned base and the SDK appends only
`/chat/completions`). Spring AI's `OpenAiApi` client uses a different convention: it always
appends `/v1/chat/completions` itself to whatever base-url is configured. I had followed
Groq's documented base URL literally, which is correct for the raw SDK but wrong for
Spring AI specifically — producing the doubled path.

The fix: changed `spring.ai.openai.base-url` from `https://api.groq.com/openai/v1` to
`https://api.groq.com/openai` (dropping the trailing `/v1`), letting Spring AI's own
default suffix complete it to the correct
`https://api.groq.com/openai/v1/chat/completions`. Re-tested the same way: the 404
"unknown URL" error was gone, replaced by a clean rejection of the placeholder API key
(expected, since no real key exists in this environment) — confirming the request was now
reaching the right endpoint. The fallback path handled both failure modes identically from
the caller's perspective, which is exactly the point of building it that way, but the
*type* of failure only became visible by reading the log, not by checking whether the
transaction still got categorized.

**Known limitation**: without a real `GROQ_API_KEY`, I could not verify an actual
successful LLM categorization end to end in this environment — only that the request now
reaches the correct URL and that the failure/fallback path works correctly. Verifying the
success path (does the LLM's category choice actually make sense for a given description)
is the one remaining check that needs a real key and a human looking at the results.

**Another real bug, same lesson as the CORS and URL-path ones — verify live, not just on
paper:** my first implementation wrapped the persistence call in `catch
(DataAccessException e)`, reasoning from Spring's exception hierarchy that "a DB failure
throws a DataAccessException." To verify the retry policy actually worked, I ran the real
app, killed the isolated test Postgres mid-flight, and watched the logs — the redelivery
mechanism itself worked immediately (any uncaught exception rolls back the JMS session
regardless of type), but my custom "Failed to persist transaction (delivery attempt N)"
warning log never printed. The actual exception was
`org.springframework.transaction.CannotCreateTransactionException` — thrown when a
*connection cannot be acquired at all* (transaction begin time), which extends
`TransactionException`, a sibling hierarchy to `DataAccessException`, not a subtype of it.
`DataAccessException` only covers failures *after* a connection is already in hand (query
errors, constraint violations). Reasoning from the exception hierarchy on paper missed
this distinction; only actually killing the database and reading which exception class
came back caught it. Fixed by broadening the catch to `RuntimeException`. Re-verified: 3
delivery attempts logged with the correct exponential backoff timing (immediate, +3.1s,
+4.1s — matching the configured 1s/2s redelivery delays plus the DB connection timeout),
then silence (no 4th attempt), then confirmed the message did not resurface even after
Postgres came back online — meaning it was actually routed to the dead-letter address, not
lost or endlessly retried. A new, unrelated transaction posted after DB recovery persisted
normally, confirming the system self-heals once the outage clears.

**JMS retry handling + structured validation errors:**

Prompt: *"Implement the following two enhancements: 1) Retry handling (the JMS listener
currently has no defined retry behavior for a failed message (bad categorization or DB
issue)). 2) Map field errors to a structured error response in GlobalExceptionHandler."*

For retry handling, "bad categorization" turned out to be a non-issue by construction:
`CategorizationService` already catches every failure internally and falls back to
rule-based matching, so it never throws — the only realistic failure point left in
`TransactionListener` is the DB save. Implemented via Artemis's own redelivery policy
(`ArtemisConfigurationCustomizer` in `JmsConfig`, configurable via
`datakite.ledger.jms.max-delivery-attempts` etc.) rather than a manual retry loop in
application code, since JMS redelivery-on-uncaught-exception is the idiomatic mechanism —
letting the exception propagate and rolling back the transacted session is the "retry
trigger," not something to catch and hide.

**Pre-submission review + catch-all exception handler:**

Asked directly: *"Do you have any enhancement/refactor suggestions before submitting the
assessment"* — surfaced (among other things) that `GlobalExceptionHandler` only handled
`MethodArgumentNotValidException`, so a malformed JSON body or any unexpected server error
would fall through to Spring's default error page instead of the structured `ErrorResponse`
shape just built for validation errors. Follow-up prompt: *"Yes, add the catch-all
exception handler."* Added two more handlers: `HttpMessageNotReadableException` (malformed
request bodies → `400`) and a true catch-all `Exception` handler (→ `500`, logs the full
exception server-side via `log.error`, but only ever returns a generic
"An unexpected error occurred" message to the client — deliberately not echoing exception
internals back over the API).

Also asked, separately: *"For #1 [testing real LLM categorization] how can I know if the
decision was taken actually from Groq or simple from the Mock service?"* — a fair question,
since nothing in the API/persisted data currently distinguishes the two. Answered honestly
that today the only way is the backend log (`CategorizationService` logs a `WARN
... falling back to rule-based matching: <reason>` only when the fallback engages; its
absence means Groq's response was used) — and explicitly did *not* add a
`categorizationSource` field to the API/UI unprompted, since that's a real (if small)
schema/API/UI change beyond what was asked; offered it as an option instead.

Verified both new handlers live, not just via the mocked `@WebMvcTest`: malformed JSON via
`curl` → clean `400`; a genuine unhandled exception by stopping the isolated test Postgres
mid-request on a `GET` call → clean `500` with the real `DataAccessResourceFailureException`
stack trace visible in the server log but never in the HTTP response body. Notably, this
GET-path failure surfaced as a proper `DataAccessException` subtype (unlike the earlier
JMS-listener write-path failure, which was a `TransactionException`) — but since the
catch-all handler catches `Exception` broadly, that hierarchy difference didn't matter
here, which is exactly why a broad catch-all is the right tool for this specific job
(unlike the narrower, type-specific catch used in `TransactionListener` for the retry
logging). 20 tests pass (2 new: malformed-JSON and unexpected-exception cases).

**Distinguishing genuine Groq calls from silent fallback:**

Prompt: *"add a test case or two where the transaction description doesn't contain any
keyword but belongs to one of the categories other than MISCELLANEOUS, I need it to check
whether Groq is functioning as it should ir no"* — a well-targeted request: since the
fallback engine defaults to `MISCELLANEOUS` whenever no keyword matches, a description
that (a) contains zero `RuleBasedCategorizationService` keywords but (b) clearly implies a
specific category to any reasonable reader turns "did Groq work?" into a simple
observable: `MISCELLANEOUS` back means the fallback silently engaged; anything else means
the LLM actually classified it correctly. Added two cases to `scripts/test-requests.sh`:
"Monthly bill from DigitalOcean for compute instances" (implies Infrastructure) and "Team
outing at Olive Garden after the sprint demo" (implies Business Meals) — both checked by
hand against the full keyword list for accidental substring collisions (e.g. "rental"
contains "rent," which would have defeated the point) before picking the final wording.

Verified the "control" case live (no real `GROQ_API_KEY` in this environment): both
descriptions correctly came back `MISCELLANEOUS` via the fallback, with the expected
`WARN ... falling back to rule-based matching` log line for each — confirming they are
genuinely keyword-free and the test actually isolates what it's meant to. This is the
piece that still needs the user's real key to complete: running the same script with a
real key and seeing `INFRASTRUCTURE`/`BUSINESS_MEALS` instead would be the positive
confirmation that Groq itself is working correctly end to end.
