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

2026-10-23 03:43 UTC — OBSERVED: First evidence checkpoint was pushed after the required tree scan; local HEAD and remote refs/heads/wip/RCOURS3-evidence-20261023 both equal f6d1877fe1c3ae4a1bb90fc76eceda36c24435a4. OBSERVED: Product diff has two production files, README, 81 new framing/length cases and the 14-case buffer suite; the existing-test change replaces unsupported custom data with real deflate while retaining buffer/stream/body checks. INFER: That change keeps the buffer-sizing purpose and deliberately changes previously undecoded unsupported-encoding behavior; the PR describes and tests the new rejection separately. INFER: Candidate tests will cover complete gzip followed by incomplete additional members, repeated encoding headers, empty/list coding values and encoded fragment progress.

## Ownership invariant register

| Resource | Creator / owner / closer | Terminal state and observable behavior | Public-path proof |
|---|---|---|---|
| Response connection and decoder | ConnectionBuilder opens; one download owner consumes; finally closes | Reject framing/decoding before cache move; HEAD is metadata only; redirect uses final response | Length/framing suites; independent encoded/list tests |
| Temporary artifact | Download owner creates/appends under canonical-file lock; structure lock protects filesystem changes | All rejection paths remove bytes before ownership release; valid resume appends only matching identity suffix | Prior A1–A7, range ending latch, concurrent rejected callers |
| Final artifact | Only owner atomically moves completed temporary file | No partial publication; a second caller sees complete bytes or waits; callers receive File handles under existing contract | Same-file success/rejection tests and pending-file snapshots |
| Saved prefix after restart | Prior owner persisted; next lock owner validates metadata before append | Full 200 replaces prefix, valid 206 completes, invalid/mismatching/encoded 206 removes prefix | Length range matrix and encoded-resume case |
| Logger callbacks / re-entry | Caller supplies logger; owner emits progress under existing ownership | No ownership change introduced; fragments must reach progress without the next fragment; no new mutable return collection | Unencoded progress and independent streaming-gzip case |

2026-10-23 03:44 UTC — OBSERVED: Authored seven independent public FileCache tests with real loopback HTTP servers in Rcours3ReviewTests.scala; no existing tests changed. OBSERVED: RFC 9110 section 8.4 defines Content-Encoding as a list allowing zero members; section 5.6.1.2 requires recipients to ignore reasonable empty elements (https://www.rfc-editor.org/rfc/rfc9110.html#section-8.4 and #section-5.6.1.2). INFER: Rejecting an empty coding or comma-empty elements is a candidate introduced valid-response rejection; remaining candidates concern repeated coding fields, incomplete second gzip headers, nested decoder EOF and streaming progress. INFER: Next checkpoint is one head run of these exact assertions, then identical source comparisons on main.

2026-10-23 03:45:59 UTC — OBSERVED: Head existing targeted register passes 95/95 in 95 unique qualified utest events and XML cases; plain utest has no retry wrapper. OBSERVED: Independent seven-case head register has 1 pass/6 fail in one attempt each; failures are empty/list coding values, repeated coding fields (two cases), incomplete second gzip header, and streaming gzip progress; nested incomplete decoder case passes. OBSERVED: Source and complete XML/logs are preserved; no existing test assertion changed. OBSERVED: Evidence checkpoint480f042cd6389fa02d7ed63dfb89e012ddf07b24 verified local=remote. OBSERVED: No Addendum headings. INFER: Next checkpoint is identical main assertions and classification of each observed path.
