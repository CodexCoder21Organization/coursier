# T8 findings

## Plan
- [in progress] Read guidance, rebase current branch, compare main and RC8 evidence.
- [pending] Add three permanent large-body loopback tests with progress handshakes.
- [pending] Prove all tests fail with whole-body buffering, restore, checkpoint evidence.
- [pending] Run each new test 30 times, framing set once, cache module once, at most two JVMs.
- [pending] Review tests and diff, push product branch, update PR description, verify remote SHAs and cleanup.

## Scope and rules
Progress-total behavior stays unchanged. No merge, queue, deployment, artifact publication, check re-request, or handoff commands. Evidence excludes fixture files. Existing assertions will remain unchanged unless explicitly recorded.

## Reading
Required: PHILOSOPHY.md and architecture/TESTING.md from current DocumentationRepository; coursier README.md; no root AGENTS.md found. RC8 review and supporting probes are being inspected.

2026-10-23 UTC — OBSERVED: Fresh clone created at the requested T8-coursier-20261020 path. OBSERVED: Fetch and rebase reports the PR branch is already current with origin/main (3788263a). OBSERVED: Current README promises incremental decoded progress within gzip members. INFER: Permanent public-API tests should guard that promise without changing progress totals. INFER: Next checkpoint is the scoped test design and evidence from RC8.

2026-10-23 09:51 UTC — OBSERVED: Read RC8 review.md and Rc8StreamingTests.scala, plus prior FINHCOURS gate and runner notes. OBSERVED: Main remains 3788263afb4b8977cfd5ebc2a557de566e0c801c; the PR's production changes are absent there, so none are superseded. OBSERVED: No Addendum headings at this checkpoint. OBSERVED: This is Scala/Mill 0.12.15, not kompile; commands use the local direct Mill launcher with -j1 and XML/event attempt auditing. INFER: Three 4 MiB tests can reuse the existing deterministic ProgressGate without editing existing cases or the harness. INFER: Single-member tests will pause after sync-flushed decoded bytes; multi-member gzip will pause inside its second member, checking partial progress rather than only member completion. INFER: Next checkpoint is permanent tests and a whole-body-buffering mutation failure for each.

## Invariants for added tests
| Resource | Creator / owner / closer | Public proof |
|---|---|---|
| Loopback response | Existing scenario owns listener, accepted sockets and executors; finally closes all | Remaining bytes withheld until decoded callback and test release; terminal download completes only afterward |
| Cache files | FileCache owns temporary/final publication; scenario owns isolated directory and removes it | At pause temporary bytes exactly match the prefix and final path is absent; at EOF exact body is cached and temporary file is gone |
| Progress | Logger owns synchronized observations; callbacks are read-only | Positive strictly increasing decoded positions, final body length, no duplicate completion, single request |
| Member stream | Test generates fixed seeded bytes and sync-flush boundary; product owns decoder | Gzip/deflate return bytes before remainder; multi-member case pauses inside member two |

Existing test/assertion edits: none. Only new helpers and three new test bodies are planned.

2026-10-23 09:54 UTC — OBSERVED: First evidence push verified local=remote=bf723bc3951a96134a39abac65d60e79ec8c0fb7 after the required clean tree scan. OBSERVED: Product commit 77612925a adds three tests and one helper, with no existing test/assertion changes and no production changes committed. OBSERVED: Whole-body buffering is temporarily inserted before the public copy/progress loop; mutation patch and test patch are retained here, and the local one-attempt run is compiling. INFER: The mutation cannot satisfy the prefix handshake because it must read the withheld tail before invoking any progress callback. INFER: This asserts early streaming, not a heap-byte bound; callbacks and persisted prefix are the public observables. INFER: Next checkpoint is the three mutation results and restored product code.
