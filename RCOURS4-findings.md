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
2026-10-23 05:01 UTC — OBSERVED: Fresh recursive clone is in RCOURS4-coursier-20261020; local review/RCOURS4-pinned rebased onto origin/main without any change, remains 6fa47c3435700763a1d2fc82dd7399d0012184a6. OBSERVED: PR is OPEN at that exact head, main remains f7ac8c67dbe757d9129cfa453a37f2867bd1272d. INFER: Main overlaps only the buffer-sizing prerequisite and does not supersede the response-validation change. OBSERVED: No PR/source edits, builds, tests, remote runs or handoff commands so far. INFER: Next checkpoint is the independent register and parser-boundary probe source, followed by single-attempt results.

2026-10-23 05:03:33 UTC — OBSERVED: First scanned evidence push verified local=remote=b80a4c6fe434600c85549c5680ebdd067d6b4868. OBSERVED: Local test-only commit adds byte-identical RCOURS3 review and decoder-ending registers; production diff from pinned head is empty. OBSERVED: Single-attempt head gate started with direct Mill 0.12.15 no-server entry and -j1 (one build JVM and one test JVM); memory preflight passed. OBSERVED: Authored 16 independent real-server blank-member boundary cases in evidence, including repeated-field checks which may reproduce inherited A2. INFER: Next checkpoint is exact XML/events for the unchanged independent register, then running the new boundary cases without editing any existing assertion.

2026-10-23 05:05 UTC — OBSERVED: The recovered direct launcher failed before compilation because out/mill-lock parent directory was absent; bootstrap-failure.log and result retained, zero test attempts. OBSERVED: Created only the standard untracked out/ directory; detached initial shell had also ended before launching tests, so the active shell now waits on the recorded child PID. INFER: Neither setup event is a product finding or a test retry; next checkpoint is the first actual independent register result.

## Review invariants and pre-registered probes
| Resource | Create / own / close | Terminal rule and caller behavior | Proof |
|---|---|---|---|
| Encoding list | URLConnection supplies field; downloader normalizes and validates | Remove only empty trimmed members; all nonempty unknown names reject; reverse supported coding order | Original A1 cases, new unknown-first/middle/last probes, final eight A1 tests |
| Response and decoding streams | Download owner opens and finally closes | Actual wire count/framing and all required decoding must complete before promotion | Fixed/chunked/range matrix; RCOURS3 decoder endings |
| Saved temporary prefix | Previous owner persists, next file-lock owner appends/replaces | Blank-only 206 is identity and must satisfy range and framing; full 200 replaces; encoded partial rejects | New fixed/chunked/short/overlong/missing-ending/pending-publication probes |
| Final cache file | Download owner moves only after success | Rejections leave no final or temporary bytes; pending chunk ending cannot publish | Success/error observation helpers and latch probe |
| Repeated encoding fields | Connection header accessor supplies only one value | All field lines should contribute to coding list; blank final field must not hide earlier coding | Two new repeated-field probes, compare identical sources against main |

## Compliance inventory (preliminary)
OBSERVED: Review edits no existing tests or production files; two unchanged independent registers are new local files, and the new blank-member register is evidence only until the first gate finishes. OBSERVED: Against main, PR changes one existing test in DownloadBufferCapacityTests.scala:267, renaming unsupported custom/raw-body case to a real deflate/decoded-body case; all capacity, buffer-observation, body and close checks remain, with encoded wire length replacing raw length. OBSERVED: PR adds a separate empty-body zero-read case in that file. OBSERVED: FCOURS5 changes the PR-added framing suite (not a main test): removes the empty string from the unknown-coding rejection list and adds an explicit success case; the three unknown-name rejection assertions are unchanged. INFER: These are documented contract corrections, not hidden assertion weakening; however the literal C criterion of no edited main tests is not met by the buffer-case edit and must be reported explicitly.
