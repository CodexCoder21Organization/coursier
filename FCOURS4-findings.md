# FCOURS4 findings

PR: https://github.com/CodexCoder21Organization/coursier/pull/7

## Plan
- [x] Locate repository, fresh clone, rebase PR branch and inspect main overlap.
- [ ] Read required documentation and review evidence; write response/ownership invariants.
- [ ] Import and extend real-server tests; run once on 7ab7bfff and save failing evidence.
- [ ] Fix A1–A7 and close all six coverage groups; verify targeted tests.
- [ ] Independent test and code review passes; run every new/changed test 30 times, cache module once.
- [ ] Rebase, push OPEN PR branch, update description; scan/push evidence, verify SHAs and process cleanup.

## Reading and initial state
2026-10-23 01:40 UTC — OBSERVED: Fresh clone at ../FCOURS4-coursier-20261020; rebase reports up to date at 7ab7bfffb49123f9f73c45791e41b6c1ae056e7a onto main f7ac8c67dbe757d9129cfa453a37f2867bd1272d. OBSERVED: Main contains buffer sizing only, no equivalent download integrity fix. OBSERVED: Read full user brief, README.md, DEVELOP.md (no root AGENTS.md), RCOURS2-findings.md and chunked-range reproducer. OBSERVED: No Addendum headings at initial read. INFER: Next checkpoint is completed required reading, invariants, and imported single-attempt test evidence. OBSERVED: No scheduling tool is available for the status-report skill; progress reports will be delivered during active work; the user's no-handoff and orchestrator-watches-CI rules take precedence over skill defaults.

## Response and ownership invariants (before implementation)
| Resource / boundary | Creator, owner, terminal state | Observable contract and test |
|---|---|---|
| Connection / body framing | ConnectionBuilder creates; download owner closes before releasing ownership | Fixed-length reads stay bounded; chunked reads must validate the zero chunk; range counts constrain response data, not message ending. Missing terminator and excess range tests. |
| Encoded body | Download owner wraps the framed input; closes decoder even if construction/read fails | Count response bytes before decoding; gzip and deflate names ignore case; unknown codings reject before writing. Encoded short-body, decoder failure, mixed-case and unknown-coding tests. |
| Temporary file / saved prefix | Download owner creates/appends under cache file lock, structure lock guards creation/removal/promotion | Publish only after framed input and decoder both finish; every rejected outcome removes the temp before ownership ends. Saved-prefix, generic chunk/gzip errors and later successful request tests. |
| Final cache file | Download owner atomically promotes validated temp; caller receives completed file | No 204/205/304 body, no prefix-only publication. No-body and paused-final-chunk tests. |
| Same-file callers | Canonical download owner serializes writers; waiter sees completion or error | Hold response until watching callback; both short callers get complete errors; a good download shares one GET. |
| Caller re-entry / persisted state | Logger callback sees only pending temporary state until validation ends; saved prefixes may resume only compatible representations | Existing paused progress and persisted-prefix tests plus paused closing chunk and encoded-resume rejection. No mutable values are returned by new code. |

2026-10-23 01:41 UTC — OBSERVED: Read complete PHILOSOPHY.md and architecture/TESTING.md, repository README.md and DEVELOP.md, both RCOURS2 reproducer sources, and response/publication paths. OBSERVED: Evidence checkpoint local=remote=6e69f96c458ae5b04d35c5ddfaf3c6b15cbe2939 after clean requested scan. OBSERVED: No Addendum headings. OBSERVED: Optional recursive submodule update reports “Unable to find current revision in submodule path modules/tests/metadata”; cache module does not depend on that test metadata checkout. OBSERVED: report-challenge skill read; its CLI merges/enqueues, which this brief expressly forbids, so this friction is recorded here only. INFER: The mechanism to fix is conflating expected data count with framing end, bypassing validation for encodings, and cleaning only direct validation exceptions; the deterministic real-server reproducers force those exact cases. INFER: Next checkpoint is importing all six coverage groups and obtaining single-attempt baseline failures.

2026-10-23 01:44:28 UTC — OBSERVED: Added 24 real-server framing scenarios, adapted from both review reproducers, in a new DownloadResponseFramingTests suite; no product implementation edited. OBSERVED: First-attempt baseline is compiling through Mill --no-server -j1; no test result yet. OBSERVED: Required reading complete and ownership invariant table recorded. OBSERVED: No Addendum headings. INFER: Next checkpoint is the baseline XML and per-finding failure inventory.
