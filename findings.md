# T8 findings

## Plan
- [completed] Read guidance, rebase current branch, compare main and RC8 evidence.
- [completed] Add three permanent large-body loopback tests with progress handshakes.
- [completed] Prove all tests fail with whole-body buffering, restore, checkpoint evidence.
- [in progress] Run each new test 30 times, framing set once, cache module once, at most two JVMs.
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

2026-10-23 09:57 UTC — OBSERVED: Second evidence push verified local=remote=a74ccc5ac8ca1d9c097e475ce06506cf3f20d018. OBSERVED: Two launcher selections stopped before executing tests; utest requires one fully qualified comma/braces query rather than a wildcard or separate unqualified names. OBSERVED: Corrected selection after reading utest 0.9.1 BaseRunner.scala and TestQueryParser.scala; launch-only logs are separate and count as zero test attempts. OBSERVED: The actual mutation has emitted two failing named test results and is waiting on the third existing 30-second handshake bound. OBSERVED: Dedicated test-comprehensiveness and separate code-review passes are in review.md; no product defect or assertion gap was identified in the added test surface. OBSERVED: No Addendum headings at the current checkpoint. INFER: Next checkpoint is 3/3 mutation failures, restoring the exact production blobs, formatting only added code, then first successful round and durable product push.

2026-10-23 09:57 UTC — OBSERVED: All three new cases fail under the whole-body decoded-buffer mutation: 0/3 pass, 3/3 fail, three unique first-attempt XML cases/events, zero retries/errors/skips and maximum two JVMs. OBSERVED: Each failure is at the original scenario progress wait in DownloadEncodingIntegrityTests.scala:288; no server remainder can be sent before that missing callback. OBSERVED: The mutation has been restored and remains only as an evidence patch. INFER: Next checkpoint is a passing same-test run on the restored product, followed by the 30-round and framing/cache gates.

2026-10-23 09:58 UTC — OBSERVED: Evidence checkpoint verified local=remote=91b829a40e8ade96596d4659f87aedf021a41b9b. OBSERVED: Restored production files match the original PR exactly; scalafmt changes only alignment of a newly added val. OBSERVED: The restored three tests pass 3/3 first attempts (zero retries/errors/skips, maximum two JVMs); XML names and events agree. OBSERVED: OPEN-state check preceded product push with --force-with-lease; product local and remote both equal 6903ce429a3c6015d8ee285a540ceab1ddf268b9. OBSERVED: Rounds 2–30 are running sequentially, with a fetch/rebase and memory check before each. INFER: Next checkpoint is 90/90 first-attempt passes, followed by the original 103-case framing register and cache module.

2026-10-23 10:01:51 UTC — OBSERVED: Evidence checkpoint verified local=remote=df032edb1b975cf071b131545f0325b04735835f. OBSERVED: 21 completed rounds pass all three cases each, with zero retries/errors/skips and at most two observed JVMs. OBSERVED: Every completed result records product head 6903ce429a3c6015d8ee285a540ceab1ddf268b9. OBSERVED: Current full brief has no Addendum headings. INFER: Next checkpoint is all 30 rounds, then sequential framing/cache gates and final PR description update.

2026-10-23 10:03:54 UTC — OBSERVED: Evidence checkpoint verified local=remote=bc2970b0b02eaeb42e4f289679f9679f39d3c781. OBSERVED: All three permanent tests pass 30/30 each: 90 unique first-attempt case events across 30 XML reports, zero retries/failures/errors/skips and maximum two JVMs. OBSERVED: All 30 runs used head 6903ce429a3c6015d8ee285a540ceab1ddf268b9; the mutation is absent and both production files equal original PR blobs. OBSERVED: The framing gate has started, to be followed by the cache module once, using testLocal to keep its benchmark child within the two-JVM budget. OBSERVED: No Addendum headings at this checkpoint. INFER: Next checkpoint is 103/103 framing and 214/214 cache, final product push/description update and process cleanup.
