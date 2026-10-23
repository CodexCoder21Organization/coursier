# FINHCOURS download integrity

## Plan
- [in progress] Read required docs and remote reviews; fresh clone/rebase and compare main; map invariants.
- [pending] Add real-loopback regressions for each of four defects; prove failure once each on main.
- [pending] Fix header combination, strict gzip completion, progress and outer-layer verification; verify each test 30 times.
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
