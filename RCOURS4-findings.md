# RCOURS4 final-head review

PR: https://github.com/CodexCoder21Organization/coursier/pull/7
Product branch: fix/FCOURS2-download-length-integrity
Pinned head: 6fa47c3435700763a1d2fc82dd7399d0012184a6
Main: f7ac8c67dbe757d9129cfa453a37f2867bd1272d

## Plan
- [completed] Read full brief, required documentation, fresh clone README and DEVELOP; verify PR, rebase locally and compare current main.
- [in progress] Trace final diff, retrieve independent registers, define blank-member boundary probes and audit invariants.
- [pending] Run independent register and blank-member probes with one attempt per case; compare inherited cases with main using identical real-server sources.
- [pending] Review test comprehensiveness and compliance; scan and push evidence at milestones; verify remote SHAs and process cleanup; deliver A/B/C.

## Scope and reading
OBSERVED: Read full-RCOURS4.md; it has no Addendum headings at startup. OBSERVED: README.md and DEVELOP.md describe Mill/Scala, no scripts/test.bash and no root AGENTS.md. OBSERVED: Retrieved PHILOSOPHY.md and architecture/TESTING.md through authenticated GitHub API; copies are retained here. OBSERVED: Read remote RCOURS3 and FCOURS5 findings, test sources and launchers. INFER: Plain utest with Mill is the correct local single-attempt gate; kompile execute/maxTestAttempts does not apply to this repository.

## Progress
2026-10-23 05:03 UTC — OBSERVED: Fresh recursive clone is in RCOURS4-coursier-20261020; local review/RCOURS4-pinned rebased onto origin/main without any change, remains 6fa47c3435700763a1d2fc82dd7399d0012184a6. OBSERVED: PR is OPEN at that exact head, main remains f7ac8c67dbe757d9129cfa453a37f2867bd1272d. INFER: Main overlaps only the buffer-sizing prerequisite and does not supersede the response-validation change. OBSERVED: No PR/source edits, builds, tests, remote runs or handoff commands so far. INFER: Next checkpoint is the independent register and parser-boundary probe source, followed by single-attempt results.
