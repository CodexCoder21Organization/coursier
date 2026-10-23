# FINHCOURS download integrity

## Plan
- [completed] Read required docs and remote reviews; fresh clone/rebase and compare main; map invariants.
- [completed] Add real-loopback regressions for each of four defects; prove failure once each on main.
- [in progress] Fix header combination, strict gzip completion, progress and outer-layer verification; verify each test 30 times.
- [pending] Run original 103-case framing register and cache module once, one attempt per case, at most two JVMs.
- [pending] Complete separate test and code review passes; fetch/rebase, verify gates, push and open PR; scan/push evidence and clean processes.

## Reading
OBSERVED: Read full-FINHCOURS.md, README.md, DEVELOP.md, PHILOSOPHY.md and architecture/TESTING.md; no root AGENTS.md exists. OBSERVED: Retrieved both RCOURS3/RCOURS4 findings and independent real-server sources. OBSERVED: This repository uses Mill 0.12.15 / Scala / plain utest, not kompile scripts, so maxTestAttempts launcher instructions do not apply; one event and XML case per test will be audited. OBSERVED: No scheduler tool is available; progress reports will be delivered within this active session. OBSERVED: Handoff, merge, enqueue, publish and remote runs are prohibited by this brief.

## Progress
2026-10-23 08:28 UTC — OBSERVED: Fresh recursive clone at FINHCOURS-coursier-20261020, new fix/FINHCOURS-download-integrity rebased on current origin/main 3788263a (no newer commits). OBSERVED: Main contains the merged response-framing change but still uses getContentEncoding, JDK GZIPInputStream, a BufferedInputStream over decoded data, and only raw response.finish. INFER: These match all four reported mechanisms; no requested fix is superseded. OBSERVED: Required brief check has no Addendum headings. INFER: Next checkpoint is four groups of failing real-server tests and their one-attempt XML.

## Invariant register
| Resource | Creator/owner/closer | Terminal state; re-entry/close/restart | Public-path proof |
|---|---|---|---|
| Encoding headers | Connection supplies; downloader parses all fields in wire order | All nonempty codings recognized; reversed decoding order; metadata immutable per request | Repeated gzip, mixed deflate/gzip, unknown plus identity/blank |
| Gzip members | Downloader constructs decoder and closes it in finally | Every started member header/body/trailer completes; no later partial member accepted | Exact cut-offs at later header/body/trailer; valid concatenation |
| Decoder stack | Downloader owns every layer | Inner EOF is insufficient; every outer layer must reach verified EOF before move; close on rejection | Corrupt/incomplete outer trailer and trailing output |
| Temporary/final files | Lock owner writes temporary then moves atomically | Rejection deletes temporary and never creates final; persisted prefix obeys existing range validation | Full-text errors and absent temporary/final checks |
| Progress callback | Caller supplies logger, downloader invokes it | Decoded available bytes report before waiting for next fragment; callback cannot publish final | Sync-flush fragment latch and pending final file check |

2026-10-23 08:29 UTC — OBSERVED: Evidence checkpoint local and remote both equal 5f4a513e899877de11a40d0d830ee4184608bf57 after a clean tree scan. OBSERVED: Added DownloadEncodingIntegrityTests with six named cases covering the four groups; later-member case walks every nonempty incomplete prefix, and outer-trailer corruption and truncation have separate tests. OBSERVED: Existing tests are unchanged; first single-attempt baseline is compiling with direct Mill no-server -j1, bounded to two JVMs. OBSERVED: No Addendum headings. INFER: Next checkpoint is baseline XML and a pushed test-only product commit before implementation.

2026-10-23 08:32:14 UTC — OBSERVED: Six new regressions fail on main in six first attempts (zero retries/errors/skips); the progress test fails at DownloadEncodingIntegrityTests.scala:277 waiting for the next fragment, and the other five fail exact body/error assertions at :342/:364. OBSERVED: All four defect groups are represented, with separate repeated-unknown and corrupt/incomplete outer cases; XML and event audit agree 0/6, max observed JVMs 2. INFER: The mechanism to fix is incomplete header selection plus decoder EOF that fails to enforce full member/layer completion; deterministic bytes and a progress latch demonstrate it. INFER: Next checkpoint is strict streaming decode and a passing same-test run.
