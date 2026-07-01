---
name: "Checkpoint"
description: "Persist a compact, current summary of this project's state, decisions, and next steps to .claude/MEMORY_LOG.md, so a future session (even after full context reset) can resume without re-deriving everything. Use proactively after finishing a meaningful chunk of work, before ending a session, before context is likely to be compacted, or whenever the user asks to checkpoint, save progress, log memory, or remember where things stand."
---

# Checkpoint

## What this does

Maintains `.claude/MEMORY_LOG.md` as this project's long-term memory. Claude Code sessions
don't share context with each other, so without this the project's history and rationale
would need to be re-explained (or re-discovered by reading code) every time. This skill
keeps that cost near-zero: a future session reads a handful of bullet points instead of
re-scanning the repo or asking the user to repeat themselves.

## When to run this

- The user explicitly asks (`/checkpoint`, "save progress", "log this", "remember this for
  next time").
- Proactively, without being asked, right after completing a non-trivial piece of work
  (a feature, a bug fix, a scaffold, a batch of file changes) — before moving on or ending
  the turn.
- Proactively when a session is running long and context is likely to be compacted or
  reset soon.

Do not run it for trivial exchanges (a single question answered, a typo fix) — that's log
noise, not signal.

## How to run it

1. Read `.claude/MEMORY_LOG.md`. If it doesn't exist, create it using the template in
   `template.md` in this skill's directory.
2. **Rewrite the "Current State" section** (don't append to it — overwrite it) so it
   reflects reality right now: 3–8 bullets covering what exists, what's stubbed vs.
   implemented, what's verified (builds/tests run), and any open decisions or blockers.
3. **Prepend one new dated entry to the top of "History"** (newest first, never delete
   older entries) summarizing *this session's* changes: what was done, key decisions and
   why, anything the user corrected or confirmed. 2–6 bullets. Use the current date.
4. Keep it terse. Rules of thumb:
   - Bullets, not paragraphs or transcripts.
   - Reference file paths instead of pasting their contents — a future session can read
     the file directly if it needs the detail.
   - Capture the *why* behind non-obvious decisions (a future session can't re-derive
     intent from a git diff alone).
   - If "History" is getting long (10+ entries), you may compact older entries down to
     one line each — but never delete the information they carry without folding it into
     "Current State" first if it's still relevant.
5. Tell the user in one short line that you checkpointed (e.g. "Checkpointed progress to
   `.claude/MEMORY_LOG.md`.") — don't narrate the full contents back to them.

## At the start of a session

Before doing exploratory work on this project, read `.claude/MEMORY_LOG.md`'s "Current
State" section first (this is also reinforced by this project's root `CLAUDE.md`). Only
read "History" if you need to understand *why* a past decision was made.
