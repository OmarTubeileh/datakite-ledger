---
name: "Docs Sync"
description: "Keep README.md and AI_LOG.md current as the DataKite ledger project evolves — these are graded deliverables per the assessment PDF, not internal notes. Use proactively after any change to setup, config, dependencies, Docker Compose, database migrations, or the API surface (update README.md), and after any AI-assisted implementation work, prompt-driven scaffolding, or bug fix (update AI_LOG.md). Triggers on: adding/changing an endpoint, service, env var, or migration; wiring up JMS or the frontend; fixing a bug the AI introduced; finishing a work session on this project."
---

# Docs Sync

## Why this exists

The assessment (`~/Downloads/DataKite-Async-Categorization-Ledger-Assessment.pdf`) lists
`README.md` and `AI_LOG.md` as mandatory deliverables, graded alongside the code. They must
be **kept current as the project evolves**, not written once at the end. This skill is the
standing instruction to do that.

Don't confuse these with `.claude/MEMORY_LOG.md` (maintained by the `/checkpoint` skill):
`MEMORY_LOG.md` is Claude's own internal continuity notes, invisible to the grader.
`README.md` and `AI_LOG.md` are human-facing deliverables that get reviewed. Keep them
polished and complete — no `TODO`s left behind once the corresponding work is actually done.

## README.md — update after any change to setup, run, or API surface

Update `README.md` whenever a change affects any of the following, so it never drifts from
the actual codebase:

- **Configure**: environment variables, `application.yml` properties, `.env` files —
  document what each one does and its default/example value.
- **Install**: new dependencies (Maven, npm), required local tools (JDK version, Node
  version, Docker), anything a fresh clone needs before it can build.
- **Run locally — backend**: exact commands to run the Spring Boot app outside Docker
  (e.g. `./mvnw spring-boot:run`), what it needs already running (Postgres, Artemis) and
  how to point it at them.
- **Run locally — frontend**: exact commands to run the Next.js app outside Docker
  (`npm install`, `npm run dev`), required env vars (`NEXT_PUBLIC_API_URL`), and the port.
- **Docker Compose**: the exact `docker compose up --build` workflow, which services it
  starts, their ports, and how to tear it down (`docker compose down [-v]`). If a service
  or port changes, update the table.
- **Database migrations**: how Flyway migrations run (automatically on backend startup),
  where they live (`backend/src/main/resources/db/migration`), and the naming convention
  for adding a new one (`V{next}__description.sql`) — update this whenever a migration is
  added or changed.
- **API surface**: keep the endpoint table and request/response examples in sync with the
  actual controller(s).

Write README.md instructions as literal, copy-pasteable step-by-step sequences (numbered
steps or fenced command blocks) — assume the reader is cloning the repo cold with nothing
already running. Verify commands actually work as written before considering the update
done (e.g. re-run the build/compose validation used during scaffolding) rather than just
describing what they're expected to do.

## AI_LOG.md — update per the assessment's exact requirements

The PDF requires three sections, verbatim:

1. **AI tooling stack** — the exact AI tools, extensions, or agents used during development
   (e.g. Claude Code and the model in use, plus anything else — Cursor, ChatGPT, Copilot).
   Update this the first time a new tool is introduced into the workflow.

2. **Prompt engineering strategy** — real examples of the prompts used to instruct the AI,
   at minimum covering: constructing the JMS asynchronous queues, and building the React
   frontend. When you (Claude) do that work in this project, capture the actual instruction
   you were given (or the prompt you used internally to drive an agent/subtask) verbatim in
   this section — not a paraphrase after the fact.

3. **Human-in-the-loop validation** — at least one concrete scenario where AI-generated code
   had a bug, a JMS connection issue, or a deprecated UI pattern, plus how it was identified,
   the debugging process, and the fix. When you hit a real bug in your own generated code
   during this project (a failed build, a wrong assumption, a test failure, a correction the
   user gave you), write it up here honestly and specifically — this is the section graders
   weight most heavily for "rigorous code reviewer" judgment, so a real example beats a
   generic one.

Also required (from the submission section): **actual start and finish times** for active
development — update these as real timestamps, not placeholders, once known.

Treat `AI_LOG.md` as an append-as-you-go audit trail: after implementing something
AI-assisted (which, in this project, is everything), add or extend the relevant section
immediately rather than leaving it for a final pass. Remove template `TODO` markers as each
part is genuinely filled in — a submitted `AI_LOG.md` should have none left.
