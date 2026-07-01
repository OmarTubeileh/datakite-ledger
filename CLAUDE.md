# DataKite Ledger — Project Instructions

## Session memory

This project keeps a persistent memory log at `.claude/MEMORY_LOG.md`, maintained by the
`/checkpoint` skill (`.claude/skills/checkpoint/SKILL.md`). It exists because each Claude
Code session starts with no memory of prior sessions — this file is how continuity survives
a context reset without spending tokens re-deriving it.

- **At the start of a session**: read `.claude/MEMORY_LOG.md`'s "Current State" section
  before exploring the codebase from scratch. It's usually enough on its own; only read
  "History" if you need the reasoning behind a past decision.
- **During/after a session**: proactively run the checkpoint skill after finishing a
  meaningful chunk of work, or whenever the user asks to save progress / remember
  something about this project.

## Documentation deliverables

`README.md` and `AI_LOG.md` are graded deliverables per the assessment PDF
(`~/Downloads/DataKite-Async-Categorization-Ledger-Assessment.pdf`) — they are not internal
notes like `MEMORY_LOG.md` above, and must never be left out of date or containing stale
`TODO`s once the corresponding work is done.

- **After any change to setup, config, dependencies, Docker Compose, migrations, or the API
  surface**: update `README.md`'s step-by-step configure/install/run instructions
  (backend, frontend, and Docker Compose) accordingly.
- **After any AI-assisted implementation work or bug fix**: update `AI_LOG.md` per the
  assessment's required format.

Full rules: `.claude/skills/docs-sync/SKILL.md`.
