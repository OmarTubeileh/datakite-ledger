# Project Memory Log

Persistent, human-readable memory for this project. Claude reads this file at the start
of a session instead of re-deriving context from scratch — much cheaper than re-reading
the whole codebase or conversation history. It is maintained by the `/checkpoint` skill
(see `.claude/skills/checkpoint/SKILL.md`).

Two sections:
- **Current State** — a living summary, overwritten each checkpoint. Read this first; it's
  almost always enough on its own.
- **History** — append-only, newest entry first. Only dig into this for the *why* behind a
  past decision. Keep entries terse — bullets, not transcripts. Never duplicate file
  contents here; reference the path and let Claude re-read the file if it needs detail.

---

## Current State

- Architecture decisions locked in: embedded in-process Artemis, single-currency v1 (no FX
  conversion), unit tests only. Categorization was originally rule-based-only (Phase 2) but
  has since been upgraded to real LLM (Groq) with rule-based fallback — see below.
- **Phase 1 (JMS wiring) done and verified end-to-end**: `POST /api/v1/transactions` →
  `202` → `TransactionProducer` publishes to embedded Artemis queue `transactions.ingest`
  → `TransactionListener` consumes it.
- **Phase 2 (fraud + categorization + persistence) done and verified end-to-end**:
  `TransactionListener` now calls `CategorizationService` (keyword-based, in
  `LinkedHashMap` order, falls back to `MISCELLANEOUS`) and `FraudValidationService`
  (`amount.compareTo(threshold) > 0`, strictly "exceeds" per the PDF), builds a `Transaction`
  entity via Lombok `@Builder`, and saves it via `TransactionRepository`. Verified by
  booting the app and querying Postgres directly for 3 cases (normal / over-threshold /
  exact-boundary) — all landed with the correct `status`/`category`.
- Config format changed: `application.yml` → `application.properties` (user's explicit
  request, no functional change — same keys, flat dotted form).
- **Phase 3 (ledger feed + category analytics) done and verified end-to-end**:
  `TransactionRepository` gained `findAllByOrderByCreatedAtDesc()` and a `@Query`-based
  interface projection `findCategoryTotals()` (GROUP BY category, aliased to a
  `CategoryTotals` nested interface); `TransactionService` maps these to
  `TransactionResponse`/`CategorySummary`. Both `GET` endpoints now return real data
  instead of throwing.
- Backend is now feature-complete end-to-end (ingest → JMS → fraud → categorize →
  persist → both `GET` endpoints all verified live).
- **Phase 4 (unit tests) done**: 14 tests, all passing, no DB/broker required —
  `FraudValidationServiceTest` (3, boundary-focused), `CategorizationServiceTest` (6, one
  per category + fallback + case-insensitivity), `TransactionControllerTest` (4, a
  `@WebMvcTest` MockMvc slice with `TransactionService` mocked via `@MockBean` — covers
  `202`/`400`/delegation). Run via `cd backend && ./mvnw test`.
- **Phase 5 (frontend) done and verified in a real browser** — the project is now
  feature-complete end-to-end, backend and frontend both. Real shadcn/ui components
  generated via `npx shadcn add` (table, card, badge, button, chart) — not hand-rolled
  lookalikes. `globals.css`/`tailwind.config.ts` completed with the actual shadcn CSS
  variable theme (the scaffold-phase versions were incomplete — see History). `lib/api.ts`
  wired to the real backend; `Dashboard` client component fetches on mount + polls every
  5s for the "real-time" feed requirement (no WebSocket — not asked for).
- **Found and fixed a real bug this phase**: backend had no CORS config, so the browser
  silently blocked every fetch from the frontend (port 3000) to the backend (port 8080) —
  invisible to `tsc`/`next build`/`curl`, only visible in an actual browser console. Fixed
  via `com.datakite.ledger.config.WebConfig` + `datakite.ledger.cors.allowed-origin`
  property (`FRONTEND_ORIGIN` env var). Full detail + how it was found: `AI_LOG.md` section 3.
- `README.md` and `AI_LOG.md` are current through Phase 5 — AI_LOG's three required
  sections are now all substantively filled in (tooling stack, JMS + frontend prompt
  examples, and the real CORS bug for human-in-the-loop validation). Only remaining
  AI_LOG gap: actual start/finish timestamps (PDF asks for the user's real wall-clock
  times — not something to fabricate).
- Known remaining gap (deliberate, documented in README): no multi-currency FX conversion
  — category analytics assumes single currency (v1 simplification, decided during Phase 1
  planning).
- User (feedback, applies beyond this project): explicitly asked me to rewrite AI_LOG.md
  to falsely claim they caught the CORS bug and told me to fix it, rather than the
  Playwright-based automated discovery that actually happened. Declined — AI_LOG.md is a
  graded audit-trail deliverable, and fabricating it would misrepresent exactly what the
  assessment evaluates. Offered two honest alternatives instead: (1) accurately document
  the Ruflo/claude-flow plugin in the tooling stack section (done — added, framed
  truthfully as "available but not what drove this session's actual implementation work"),
  and (2) invited the user to do a real review/test pass so a genuine human-caught finding
  could be logged. See `AI_LOG.md` section 1 for the Ruflo entry.
- `scripts/test-requests.sh` (added after Phase 5) now covers all 5 categories, not just
  3 — added SaaS/Software ("Annual software license renewal for design tool") and
  Miscellaneous-fallback ("Gift card for employee appreciation") cases, verified live
  against a real backend before shipping. `AI_LOG.md`/`MEMORY_LOG.md` checkpointing after
  small script-only changes like this is being done lightly (a couple of lines), not with
  the full phase-report treatment used for Phases 1-5.
- **Ledger feed filters (date range, amount range, category, status) + responsive design
  pass — done and verified.** Client-side filtering in `LedgerTable` (no backend changes —
  filters a `useMemo` over the already-fetched list), added shadcn `input`/`select`/`label`
  components. Responsive: filter grid 4→3→1 columns by breakpoint, table already had
  horizontal scroll via shadcn's built-in wrapper (verified it's real scroll, not clipped
  content), added a "swipe to see more" hint below `sm`, angled the category-chart X-axis
  labels so 5 category names don't overlap on narrow widths. Verified with Playwright at
  1280px/768px/375px: no horizontal page overflow at any width, zero console errors, and
  filter combinations (category, amount range, status, clear) all narrow/restore the table
  correctly.
- **⚠ Standing caution for any future verification on this machine**: this is a shared
  machine — the user runs their own `docker compose up` stack on the *standard* ports
  (`datakite-backend`:8080, `datakite-frontend`:3000, `datakite-postgres`:5432), and it's
  often left running between turns. If you start a local `./mvnw spring-boot:run` or
  `next dev` without checking first, it can silently fail to bind (or worse, silently
  succeed on a port you didn't expect) and your "isolated" test ends up hitting the user's
  live containers instead — this happened once already (see History below). **Before any
  from-scratch verification**: run `docker ps` first, and if the user's stack is up, use
  non-default ports throughout (`SERVER_PORT`, a separate Postgres container/port, `next
  dev -p <port>`, matching `FRONTEND_ORIGIN`/`NEXT_PUBLIC_API_URL`) rather than assuming
  8080/3000/5432 are free.
- **`Makefile` added** — directly motivated by the port-collision incident above.
  `make up`/`down`/`build`/`logs`/`ps` wrap plain `docker compose`; `make kill-ports`
  stops this project's named containers *and* kills stray local processes (fuser → lsof →
  ss fallback chain) still bound to 3000/8080/5432 — the exact class of problem that
  caused the earlier mistake; `make clean-db` wipes the Postgres volume
  (`docker compose down -v`); `make buildRun` = kill-ports + rebuild + start (DB kept);
  `make freshBuildRun` = kill-ports + clean-db + rebuild + start (DB wiped). Verified via
  `make -n` dry-runs (confirmed correct command sequencing) and by testing the port-kill
  fallback chain against a disposable dummy process on a throwaway port — deliberately did
  **not** run `kill-ports`/`buildRun`/`freshBuildRun`/`down` for real against the default
  ports, since the user's own stack was live at the time and those targets are destructive
  to exactly the containers/ports that stack occupies.
- **Real LLM categorization (Groq) — done, but success path unverified.**
  `CategorizationService` now calls Groq (`openai/gpt-oss-20b`, Spring AI 1.0.5's
  OpenAI-compatible client) and falls back to the renamed `RuleBasedCategorizationService`
  (old `CategorizationService` logic, unchanged) on any failure. Config:
  `GROQ_API_KEY` env var (placeholder `none` default so the app still boots with no key),
  `spring.ai.openai.base-url=https://api.groq.com/openai` (no trailing `/v1` — see the real
  bug below), model `openai/gpt-oss-20b`. 18 unit tests pass (4 new, mocking `ChatClient`
  via Mockito `RETURNS_DEEP_STUBS`). **Not verified**: an actual successful LLM
  categorization — this environment has no real Groq API key, so only the fallback path
  could be exercised live. The user should test the success path themselves with a real
  key before considering this fully done.
- Spring AI/Groq version research (worth remembering as a pattern, not just a fact): looked
  up current info instead of trusting training data for "what free LLM APIs exist" and
  "what Groq models are current" — good thing, since `llama-3.3-70b-versatile` and
  `llama-3.1-8b-instant` (the obvious first-instinct picks) had been deprecated by Groq on
  2026-06-17, ~2 weeks before this session. Also confirmed Spring AI 2.0 requires Spring
  Boot 4 (incompatible with this project's Boot 3.3.4) before picking Spring AI 1.0.5.
- **Project now has its own dedicated git repo** (`~/datakite-ledger/.git`, branch `main`,
  initial commit `2baa81a`) — it used to be an untracked subdirectory of a git repo rooted
  at `/home/omartubeileh`. User is setting up a public GitHub remote
  (`github.com/<user>/datakite-ledger`) for assessment submission; push is left to the user
  deliberately (their account, their call). `gh` CLI is not installed in this environment.
- **User (feedback, applies beyond this project): pasted real secrets twice** — once
  directly in chat (a Groq API key), once by hardcoding it into `docker-compose.yml`'s
  `${VAR:default}` and later into `.env.example` (both NOT gitignored, unlike `.env`).
  Both caught and fixed before any commit happened. Standing practice going forward: when
  a user is about to add a secret to config, proactively steer them to a gitignored `.env`
  file from the start rather than fixing it after the fact; flag immediately (don't wait to
  be asked) if a real-looking secret appears in any file that isn't gitignored, and treat
  any secret that appeared in the chat transcript itself as compromised regardless of where
  it ends up.
- **JMS retry/DLQ + structured validation errors — both done and verified live.**
  `JmsConfig` gained an `ArtemisConfigurationCustomizer` bean setting max-delivery-attempts
  (default 3), exponential redelivery backoff (1s→2s, capped 10s), and dead-letter-address
  `DLQ` — all configurable via `datakite.ledger.jms.*` properties.
  `GlobalExceptionHandler` now returns a structured `ErrorResponse`/`FieldErrorDetail` JSON
  body (timestamp/status/error/message/fieldErrors) instead of a raw exception message for
  `MethodArgumentNotValidException`. 18 tests still pass (updated the invalid-payload test
  to assert the new JSON shape). Retry policy itself isn't unit-tested (broker behavior,
  needs a real Artemis instance) — verified live instead, see History.

## History

### 2026-07-01 — Git repo + secrets incidents + JMS retry/DLQ + structured errors
- Set up a dedicated git repo for the project (previously an untracked subdirectory of the
  home-directory repo): `git init -b main`, verified `.env`/`node_modules`/`target` etc.
  properly excluded via `git add -A --dry-run` before staging, committed all 68 files as
  the initial commit. User is adding a public GitHub remote themselves (no `gh` CLI here);
  push deliberately left to them.
- **Two secret-leak incidents, both caught before any commit**: (1) user pasted a real
  Groq key directly in a chat message — advised rotating it. (2) user then hardcoded that
  same key as a literal default in `docker-compose.yml`
  (`${GROQ_API_KEY:gsk_...}` — also technically invalid Compose syntax, missing the `-` in
  `:-`), which would have been committed and pushed to the public repo. Fixed to
  `${GROQ_API_KEY:-none}`, set up `.env`/`.env.example` pattern (`.env` already gitignored
  from the initial scaffold). (3) user then pasted a *new* key into `.env.example` itself
  (not gitignored) — fixed again. Saved a standing cross-project feedback memory about
  this (see `~/.claude/projects/-home-omartubeileh/memory/feedback_secrets_in_files.md`)
  since it's a pattern worth watching for beyond this one project. Final state:
  `git grep -n "gsk_"` across the committed tree confirmed clean (only a placeholder
  example remains in README.md).
- **JMS retry/DLQ**: added `ArtemisConfigurationCustomizer` in `JmsConfig` (max-delivery-
  attempts, exponential redelivery delay, dead-letter-address, all via
  `datakite.ledger.jms.*` properties), and had `TransactionListener` log delivery attempts
  via the `JMSXDeliveryCount` message header before rethrowing. Verified live: stopped the
  isolated test Postgres mid-flight, watched exactly 3 delivery attempts fire with the
  correct exponential timing, confirmed no 4th attempt, confirmed the message did not
  resurface after Postgres came back online (i.e. it actually reached the DLQ, not lost or
  retried forever), and confirmed a fresh transaction posted after recovery processed
  normally.
- **Found via that live test, not from reading the exception hierarchy**: a DB
  connection-*acquisition* failure throws `CannotCreateTransactionException`
  (`TransactionException`, not `DataAccessException`) — my first `catch
  (DataAccessException e)` silently missed logging it (the redelivery itself still worked,
  since it doesn't depend on the catch block — only the observability log line was
  affected). Broadened to `catch (RuntimeException e)`. Same recurring lesson as the CORS
  bug and the Groq URL-doubling bug: verifying live surfaces exactly the class of mistake
  that reasoning from documentation/hierarchy alone does not.
- **Structured validation errors**: added `ErrorResponse`/`FieldErrorDetail` records in
  `dto/`, `GlobalExceptionHandler.handleValidation()` now returns them instead of the raw
  `MethodArgumentNotValidException` message. Updated `TransactionControllerTest`'s invalid-
  payload test to assert the JSON shape (`jsonPath` on status/error/message/fieldErrors).
  Verified live via curl too, not just the mocked test.
- User explicitly asked for their prompt to be logged in `AI_LOG.md` — done (section 2).

### 2026-07-01 — Real LLM categorization via Groq (and a real URL bug)
- User asked "are there free LLM options" then explicitly chose Groq. Researched current
  info first rather than trusting training data (pricing/model availability go stale
  fast): confirmed Groq/Gemini/OpenRouter free tiers, and specifically checked Groq's
  *current* model list — good call, since the obvious model names
  (`llama-3.3-70b-versatile`, `llama-3.1-8b-instant`) were deprecated 2026-06-17, days
  before this session. Landed on `openai/gpt-oss-20b`. Also checked Spring AI's version
  requirements before adding the dependency: Spring AI 2.0 needs Spring Boot 4 (this
  project is on 3.3.4), so used Spring AI **1.0.5** (the GA line supporting Boot 3.3+).
- Refactored categorization into two classes rather than bolting LLM logic onto the
  existing one: renamed the old `CategorizationService` to `RuleBasedCategorizationService`
  (logic untouched), and made `CategorizationService` the new LLM-primary orchestrator
  (`ChatClient` from `spring-ai-starter-model-openai`, catches *any* exception and
  delegates to `RuleBasedCategorizationService`). This kept all 6 existing keyword-logic
  tests intact (just renamed) and made the new LLM behavior (parsing, fallback-on-error,
  fallback-on-unparseable-response) independently testable via Mockito
  `RETURNS_DEEP_STUBS` on `ChatClient` — 4 new tests, 18 total, all passing.
- **Found a real bug via live verification, not just mocked tests**: booted the app with
  the placeholder `GROQ_API_KEY=none` and POSTed a real transaction to exercise the actual
  HTTP call to Groq (not mocked). The transaction still categorized correctly via fallback,
  which could have looked like "done" — but the log showed the real failure was `HTTP 404
  Unknown request URL: POST /openai/v1/v1/chat/completions` — a doubled `/v1/v1/` path.
  Root cause: Groq's own docs show the base URL as `https://api.groq.com/openai/v1`
  (matching the raw OpenAI SDK convention), but Spring AI's `OpenAiApi` client always
  appends `/v1/chat/completions` itself regardless — so including `/v1` in the configured
  base-url double-counts it. Fixed by setting `spring.ai.openai.base-url` to
  `https://api.groq.com/openai` (no trailing `/v1`). Documented this exact gotcha in both
  `application.properties` (inline comment) and `AI_LOG.md`, since it's a non-obvious
  mismatch between two different "OpenAI-compatible" conventions.
- **Genuine remaining gap**: no real `GROQ_API_KEY` exists in this environment, so the
  *successful* LLM categorization path has never actually been exercised — only startup
  (with placeholder key) and the failure/fallback path (confirmed the request now reaches
  the right URL and gets a clean rejection, not a 404). The user needs to test the success
  path themselves with a real key.

### 2026-07-01 — Ledger filters + responsive design (and a verification-process bug)
- Added `date range`/`amount range`/`category`/`status` filters to `LedgerTable`, entirely
  client-side (`useState` + `useMemo`, no new API params) since the dataset is small and
  already fully fetched by `Dashboard`'s polling.
- Added shadcn `input`, `select`, `label` components (`npx shadcn@latest add input select
  label`). Responsive tweaks: `page.tsx` padding/heading size scale down on mobile, filter
  grid `grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4`, `CategoryChart`'s X-axis
  ticks angled -25° with `interval={0}` so all 5 category names stay legible at narrow
  widths, added a `sm:hidden` "swipe table to see more" hint since the table's horizontal
  scroll (already present via shadcn's own wrapper div) had no visual affordance.
- **Made a real mistake mid-verification and caught it**: while setting up what I intended
  as an isolated backend+Postgres+frontend for browser testing, `curl`/Playwright kept
  returning data inconsistent with what I'd just seeded. Root cause: this machine already
  had the user's own `docker compose up` stack running from an earlier turn on the
  standard ports (8080/3000/5432), so my local `./mvnw spring-boot:run` failed to bind
  port 8080 and crashed on startup — everything I'd been testing was actually hitting the
  user's live `datakite-backend`/`datakite-postgres` containers, including one
  `scripts/test-requests.sh` run that added ~5 real rows to their running database. Caught
  it by checking `ss -tlnp`, `docker ps -a`, and the crashed backend's own log
  ("APPLICATION FAILED TO START ... Port 8080 was already in use"). Told the user
  immediately and transparently, then redid the entire verification on explicit
  non-default ports (`SERVER_PORT=8090`, a separate Postgres on 15432, frontend via `next
  dev -p 3001`) so it couldn't collide again. Did not touch or clean up the user's own
  containers — left them exactly as found.
- Verified via Playwright at 3 viewports (1280/768/375px): no horizontal overflow at any
  width, filter interactions (category-only, amount-min-only, category+status combined,
  clear-all) all produced the correct row counts and content, zero console errors. Also
  specifically checked the mobile table's horizontal scroll was real (`scrollWidth >
  clientWidth`, then actually scrolled it and confirmed clipped columns became visible)
  rather than just eyeballing a screenshot where content looked cut off.

### 2026-07-01 — Phase 5: frontend + a real bug (CORS)
- Generated real shadcn/ui components (`npx shadcn@latest add table card badge button
  chart`) instead of hand-writing lookalikes — this also revealed the initial-scaffold
  `globals.css`/`tailwind.config.ts` were incomplete (no CSS variable theme, so classes
  like `bg-card`/`text-muted-foreground` referenced by the generated components didn't
  resolve to anything). Completed the standard shadcn "slate" theme setup in both files.
- Added `CATEGORY_LABELS`/`STATUS_LABELS` maps in `lib/types.ts` for human-readable
  display (e.g. `SAAS_SOFTWARE` → "SaaS/Software", matching the PDF's exact category
  names). `LedgerTable` uses shadcn `Table`/`Badge` (status color-coded:
  `PENDING_REVIEW`/`FAILED` → destructive red, `CATEGORIZED` → secondary). `CategoryChart`
  uses shadcn's `ChartContainer` wrapping a Recharts `BarChart`.
- `Dashboard` (`"use client"`) fetches both endpoints on mount and polls every 5s —
  decided against WebSockets/SSE since the PDF only asks for a "real-time ledger feed,"
  and polling is the simplest thing that satisfies that without adding infrastructure the
  assessment doesn't ask for.
- **Real bug found via actual browser testing, not type-checking**: `tsc --noEmit` and
  `next build` both passed clean, which would normally read as "done" — but per the
  project's own standing rule (verify features actually work, not just that they compile),
  installed a headless Chromium via Playwright (`npx playwright install chromium`; system
  deps needed sudo which isn't available here, but the browser itself ran fine without
  them) and drove the real dashboard against the real backend. Console showed a CORS
  block: Spring Boot doesn't enable cross-origin requests by default, and nothing had
  configured it, so every browser fetch from :3000 to :8080 was silently rejected — a
  failure mode `curl` can never surface (no `Origin` header, so no browser CORS
  enforcement applies) and compilation obviously can't catch either.
- Fix: `com.datakite.ledger.config.WebConfig` (`WebMvcConfigurer.addCorsMappings` on
  `/api/**`), configurable via `datakite.ledger.cors.allowed-origin`
  (`FRONTEND_ORIGIN` env var, defaults to `http://localhost:3000`). Re-ran the same
  Playwright script after the fix: 0 console errors, all 3 seeded rows rendered correctly,
  3 chart bars — screenshot confirmed visually correct. Re-ran `./mvnw test` to confirm no
  regression (still 14/14).
- Environment notes for next session: this sandbox has no Docker Hub access, no
  passwordless sudo, and Playwright's `--with-deps` needs sudo too — but a plain
  `npx playwright install chromium` (no `--with-deps`) downloads a working browser anyway.

### 2026-07-01 — Phase 4: unit tests
- Added `FraudValidationServiceTest`, `CategorizationServiceTest` (plain JUnit, no Spring
  context — both services have no framework dependencies beyond constructor injection of
  a `BigDecimal`), and `TransactionControllerTest` (`@WebMvcTest(TransactionController.class)`
  + `@MockBean TransactionService`, using MockMvc).
- Deliberately mirrored the edge cases already probed manually in Phase 2/3 (the $5,000
  boundary — exactly-at vs. above — one case per category, the Miscellaneous fallback,
  case-insensitivity, and a `400` for an invalid ingest payload exercising
  `GlobalExceptionHandler`) so they're now regression-protected, not just checked once by
  hand.
- Confirms the "unit tests only" decision (vs. Testcontainers) still gives real coverage
  of the business logic and the HTTP contract without needing Docker/Postgres/Artemis —
  all 14 tests pass with `./mvnw test` alone.
- No bugs found — all passed first run. `AI_LOG.md` section 3 updated to note this
  (tests written to lock in already-verified behavior, not to catch a new defect).

### 2026-07-01 — Phase 3: ledger feed + category analytics endpoints
- Added `TransactionRepository.findAllByOrderByCreatedAtDesc()` and
  `findCategoryTotals()` (custom JPQL `@Query` with `GROUP BY t.category`, returned via a
  nested `CategoryTotals` interface projection — aliases `category`/`totalAmount`/
  `transactionCount` in the query match the projection's getter names). Implemented
  `TransactionService.listAll()`/`summarizeByCategory()` to map these to
  `TransactionResponse`/`CategorySummary`; removed the stale `// TODO Phase 3` comments in
  `TransactionController`.
- Documented the single-currency v1 simplification explicitly in README (raw `amount` sum,
  no FX conversion) since it's a known, deliberate gap rather than an oversight.
- Verified live: specifically worth noting *why* this phase got a live check rather than
  just a compile check — interface-based JPQL projections compile fine even when the
  alias-to-getter binding is wrong, and only misbehave at runtime. POSTed 4 transactions
  across 3 categories, hit both `GET` endpoints, confirmed the feed ordering and the
  per-category sums/counts were correct. Worked first try; logged in `AI_LOG.md` section 3
  as an example of *why* verification matters even when nothing was actually broken.

### 2026-07-01 — Phase 2: fraud validation + categorization + persistence
- Implemented `FraudValidationService` (`BigDecimal.compareTo` against
  `datakite.ledger.fraud.review-threshold-usd`), `CategorizationService` (ordered keyword
  map: Infrastructure → SaaS/Software → Business Meals → Operations → Miscellaneous
  fallback), fleshed out `Transaction` entity (Lombok `@Getter @Setter @NoArgsConstructor
  @AllArgsConstructor @Builder`, `@PrePersist`/`@PreUpdate` for timestamps), and wired all
  three into `TransactionListener`.
- Business-rule interpretation decision (PDF wording is ambiguous): "overridden to
  PENDING_REVIEW before the AI categorization result is persisted" was read as *override
  the status, not skip the categorization* — category is still computed and persisted for
  over-threshold transactions, only `status` becomes `PENDING_REVIEW` instead of
  `CATEGORIZED`. Documented explicitly in README's Business Rules section and in code
  comments since this was a judgment call, not a literal spec value.
- Same-turn request: replaced `application.yml` with `application.properties` (flat
  dotted-key equivalent, no behavior change).
- Verified live: reused the workaround from Phase 1 (locally cached `postgres:latest` via
  plain `docker run` on port 15432, since this sandbox has no Docker Hub access or
  passwordless sudo). POSTed 3 transactions (129.99/AWS→expect INFRASTRUCTURE+CATEGORIZED,
  7500/restaurant→expect BUSINESS_MEALS+PENDING_REVIEW, exactly 5000/office supplies→expect
  OPERATIONS+CATEGORIZED not flagged) and confirmed all three via
  `SELECT amount, description, status, category FROM transactions`. All correct on first
  run — no bugs found, so `AI_LOG.md` section 3 is still open pending a real one.

### 2026-07-01 — Phase 1: JMS wiring (embedded Artemis)
- Implemented `JmsConfig` (Jackson `MessageConverter` bean reusing Spring's `ObjectMapper`
  for correct `OffsetDateTime` handling), `TransactionProducer.publish()`,
  `TransactionListener.onMessage()` (logs only for now), wired
  `TransactionController` → `TransactionService` → `TransactionProducer`.
- Decision: switched messaging from containerized Artemis (original scaffold) to
  **embedded in-process Artemis**, per user's explicit choice when presented with the
  three options (embedded / containerized / Spring events) — matches the PDF's literal
  "in-memory JMS broker" phrasing and removes a container from docker-compose.
- Also decided (not yet acted on): rule-based mock NLP for categorization (Phase 2),
  single-currency v1 for the analytics chart (no FX conversion), unit-tests-only for
  Phase 4.
- Verified live: no Docker Hub access in this environment (registry auth fails), and no
  passwordless sudo to provision the host's local Postgres — worked around by running a
  locally cached `postgres:latest` image directly via `docker run` on port 15432, then
  `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/datakite_ledger ./mvnw
  spring-boot:run`. Confirmed `202` response and saw the message actually consumed in the
  Artemis audit log + `TransactionListener` log line. Cleaned up the test container and
  `target/` afterward.

### 2026-07-01 — Initial scaffold + persistent memory setup
- Read the assessment PDF (`~/Downloads/DataKite-Async-Categorization-Ledger-Assessment.pdf`)
  and scaffolded the project at `~/datakite-ledger` (structure only, per user's choice).
- Decisions: Java 17 over Kotlin; Maven over Gradle (default, not asked); "structure only"
  scope — no working slice yet.
- Added this memory log + a `/checkpoint` skill, scoped to this project only (not global),
  per user's choice.
