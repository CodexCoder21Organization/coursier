# RCOURS3 review findings

PR: https://github.com/CodexCoder21Organization/coursier/pull/7

## Plan

- [in progress] Read required documentation, fresh clone README/instructions, prior remote evidence, confirm head and rebase locally; compare main for overlap.
- [pending] Trace response framing, decoding, range, cleanup, streaming progress, redirects, HEAD and cache ownership; write invariant and boundary register.
- [pending] Run targeted single-attempt tests for prior findings and new candidate defects on main and head using real local servers.
- [pending] Review coverage and compliance, secret-scan and push evidence at milestones, verify remote SHA, finish three verdicts and process cleanup.

## Progress

2026-10-23 initial checkpoint — OBSERVED: The PR is OPEN at ac74fa03ab018e66a67f293dbae7a4fe3a7bd367 on fix/FCOURS2-download-length-integrity. OBSERVED: The full brief was read and contains no Addendum heading. OBSERVED: A fresh recursive clone is in progress in the required RCOURS3-coursier-20261020 directory. INFER: Next checkpoint is local rebase and a diff/boundary register after required reading. OBSERVED: This is report-only work; no product branch push, check re-request, merge, artifact publication or handoff command is authorized.

2026-10-23 03:42 UTC — OBSERVED: Fresh local review/RCOURS3-head rebase is up to date, HEAD remains ac74fa03ab018e66a67f293dbae7a4fe3a7bd367, merge-base/main is f7ac8c67dbe757d9129cfa453a37f2867bd1272d. OBSERVED: Read README.md, DEVELOP.md; no root AGENTS.md; repository uses Mill/Scala, not kompile scripts. OBSERVED: Read PHILOSOPHY.md and TESTING.md plus RCOURS2 findings and FCOURS4 findings/runner; cache tests extend plain utest TestSuite with no retry mixin. OBSERVED: No Addendum headings. OBSERVED: The 95-case targeted head register is starting once with Mill --no-server -j1. INFER: Main's earlier buffer sizing overlaps only that prerequisite; none of this PR's response framing or decoding changes is superseded. INFER: Next checkpoint is head XML and additional candidates after the decoder/stream-progress audit.
