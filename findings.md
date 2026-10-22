# RCOURS fresh-context review

PR: https://github.com/CodexCoder21Organization/coursier/pull/6

## Plan
- [x] Read brief, locate fork, fresh clone, rebase locally onto current main, inspect overlap.
- [x] Read required documentation and prior remote evidence; audit product and test changes.
- [x] Create targeted real-server candidate tests and run once each against base and head.
- [x] Review comprehensiveness and compliance, record three verdicts.
- [ ] Scan, push evidence, verify remote SHA, check process cleanup.

## Progress
2026-10-22 21:53 UTC — OBSERVED: Fresh clone is at the required RCOURS-coursier-20261020 path; local rebase of fix/I419i-response-download-buffers onto origin/main was a no-op. OBSERVED: main is 7434805e35d9941f840c6e8c914f7d53fe852e10 and PR head is 2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8; main does not contain the sizing diff. OBSERVED: No Addendum headings in full-RCOURS.md. INFER: The PR remains relevant; next checkpoint is documentation and protocol-path audit with a runnable targeted probe.

2026-10-22T21:55:34.092171+00:00 — OBSERVED: Read complete PHILOSOPHY.md and architecture/TESTING.md from DocumentationRepository main, root README.md and DEVELOP.md; no root AGENTS.md exists. OBSERVED: Prior evidence RESUME.md, existing-test-edits.md, repeat-coursier.py, test-corrections.patch and code-review.md were read directly from origin/wip/I419i-evidence-20261022. OBSERVED: Evidence checkpoint local and remote both e19f686cc2ecd27914c0765643b3e1521e565423 after the required scan. OBSERVED: CacheUrl.rangeResOpt does check that Content-Range starts with the requested offset; the earlier suspicion of no validation was refuted by this source read. INFER: Real-server tests must verify wrong start rejection and matching-start but inconsistent end/total independently; next checkpoint is targeted compilation and candidate server cases.

2026-10-22T21:57:47.512474+00:00 — OBSERVED: Initial cache JVM test compilation completed all 261 Mill tasks with zero compiler errors. OBSERVED: The recursive clone completed and both submodules now match the PR tree. OBSERVED: Wrote 17 new real-server candidate scenarios in RcoursResponseTests.scala; no existing test source was edited. OBSERVED: Current cgroup memory.events has oom_kill=2 before the probe; this is a starting counter, not an event attributed to this lane. OBSERVED: No Addendum headings at this checkpoint. INFER: Next checkpoint is single-attempt head/base results and comparison; unchanged base will be selected by restoring only Downloader.scala from current main, preserving identical final test source.

2026-10-22T22:00:11.470553+00:00 — OBSERVED: Head targeted register has 27 passed / 3 failed / 30, and base has 16 passed / 14 failed / 30, each XML naming every case once with no skipped cases. OBSERVED: The 13 PR cases pass 13/13 on head and 5/13 on base, with eight size-assertion failures. OBSERVED: Of the 17 new cases, head passes14 and fails3; base passes11 and fails6. OBSERVED: Overstated length and inconsistent Content-Range end/total fail identically on both; the understated-length over-read, tiny exact-size and cap boundary cases fail only on base. OBSERVED: First new-harness invocation failed compilation because FileCache uses withRetry, not withRetryCount; corrected that API name before any case ran and retained the compile-error log separately. INFER: Three response-integrity defects are present before this PR, while the smaller buffer improves the understated-length case; next checkpoint is a paused-body hang probe and the final review report.

2026-10-22T22:02:38.867219+00:00 — OBSERVED: The paused real-server case failed once on base and once on head at the same rejection assertion; both public futures remained pending after the six-byte progress callback until the server released EOF, then returned a six-byte successful file despite Content-Length9. OBSERVED: No new-harness case has been retried; the earlier API-name compile failure ran zero test bodies. OBSERVED: Prior checkpoint local and remote both3978f29fbabddfc54cea9d9d07b7befb69064b79 after the scan and push. OBSERVED: OpenJDK runtime is Temurin21.0.12.1; default transport request captured by the server is HTTP/1.1 without Upgrade or HTTP2-Settings. INFER: A true HTTP/2 DATA-frame case does not belong to the default URLConnection transport; that premise is refuted for this production path, with the custom-provider limit to be stated. INFER: Next checkpoint is the test-gap/compliance report, attempt audit and final cleanup.

2026-10-22T22:09:13.411200+00:00 — OBSERVED: The deterministic fragmented-length case fails1/1 on each version: base saves6bytes and head saves5bytes for Content-Length3 with six actual bytes. OBSERVED: All64 measured testcase attempts are accounted for: head27passed/5failed/32 and base16passed/16failed/32, with no retries or skips. OBSERVED: No Addendum headings at this checkpoint. OBSERVED: Full review.md records A APPROVE0 for new patch defects, B GAPS5 for permanent coverage, and C PASS while explicitly identifying the changed HEAD assertion contract and four existing response-handling problem classes. INFER: Next checkpoint is a refreshed cleanup check, scan, final push and verified remote SHA.

# RCOURS review

PR: https://github.com/CodexCoder21Organization/coursier/pull/6

## Verdicts

**A: APPROVE (0 new patch defects).** The valid-response buffer change is bounded and preserves the read-until-EOF loop. This is a verdict on the change, not a claim that FileCache safely rejects every malformed HTTP response. The tests below find four existing response-handling problems: accepting an incomplete fixed-length response, reading beyond an understated length, accepting an inconsistent range end, and accepting an inconsistent range total. All reproduce on main as well as head. The understated-length output also changes with this patch; that difference and its exact limitation are reported below rather than called equivalent behavior.

**B: GAPS (5).** The 13 PR cases correctly reproduce the buffer issue and cover the ordinary response-size contract. Permanent coverage still misses malformed length handling, malformed range end/total handling, Long-sized numeric boundaries, redirects, and a direct check of the decoded-side gzip buffer contract. Details below distinguish missing coverage from a demonstrated implementation defect.

**C: PASS.** No introduced reflection or mocks; no raised waits or reduced iteration counts. The HEAD-count edit corrects an unmerged test's wrong expectation. It changes that test assertion's contract and is not a logical strengthening of `> 0`.

## Source and main comparison

Fresh checkout: `workspace/RCOURS-coursier-20261020`. Read README.md and DEVELOP.md; no root AGENTS.md exists. Local rebase of `fix/I419i-response-download-buffers` onto current main was a no-op. Main remains `7434805e35d9941f840c6e8c914f7d53fe852e10`; PR remains `2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8`, OPEN and unmerged. Main does not contain an equivalent sizing change. No PR-branch push, PR edit, merge, publication, deployment, or remote build was made by this review.

The product diff is limited to README.md and Downloader.scala, plus the newly introduced test suite and the checkpoint's submodule pointer. The two implementation commits are https://github.com/CodexCoder21Organization/coursier/commit/3706f14db08eed0e85ac2907f64a9f10a9298abb and https://github.com/CodexCoder21Organization/coursier/commit/2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8. Review source: https://github.com/CodexCoder21Organization/coursier/blob/2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L271.

`min(bufferSize.toLong, max(1L, responseLength)).toInt` takes the configured cap before narrowing. For a positive Int cap its result is in `1..bufferSize`, even at Long.MaxValue. Both the BufferedInputStream and transfer array get that value. Unknown lengths and non-identity content encodings retain the configured cap. The persisted prefix is added only for progress accounting, not allocation. The unchanged copy loop reads until stream EOF; buffer capacity is not used as a byte-count termination condition.

## Runs and single-attempt audit

All builds/tests ran locally, sequentially, after fetch/rebase and `free -m`; no full suite or downstream gate was authorized or started. Mill0.12.15 / Scala2.13.16 / utest0.9.1 ran on Temurin21.0.12.1. The cache test module compiled successfully before the probes. The first new-harness invocation used the wrong API spelling `withRetryCount`, failed compilation, and executed zero test bodies. It was corrected to the public `withRetry(0)` before measurement; no failed case was retried.

| Register | Base passed / failed / total | Head passed / failed / total | Attempts |
| --- | --- | --- | --- |
| Unedited final PR suite | 5 / 8 / 13 | 13 / 0 / 13 | one per case per version |
| New response matrix | 11 / 6 / 17 | 14 / 3 / 17 | one per case per version |
| Paused incomplete response | 0 / 1 / 1 | 0 / 1 / 1 | one per version |
| Fragmented understated length | 0 / 1 / 1 | 0 / 1 / 1 | one per version |
| **Total** | **16 / 16 / 32** | **27 / 5 / 32** | **32 first attempts each; zero retries** |

No skipped cases or XML errors occurred. `attempt-audit.json` cross-checks unique XML cases with execution records. utest's default Executor executes the body once; its retry behavior requires the explicit `TestSuite.Retries` mixin, absent here and in the PR suite. `utest-retry-audit.md` records the exact upstream source. The three scenarios inside the cap case and four lengths inside the Long case are retained subchecks, not separate testcase attempts.

For the paired base measurement only Downloader.scala was restored from current main. Its Git blob was `85d40a70dd32ffdda046f9e8f531aff6b3d14f5c`; head's blob was `f7107a9b1b6482417256f884d99e22c4987ba0a7`. Test source was identical across each pair. The measurement patch and source bindings are saved. The checkout was returned to the original, clean PR branch after testing. No product fix was made.

## A: candidate-by-candidate result

The new tests use FileCache.file, a fresh temporary cache, checksums explicitly disabled to isolate HTTP handling, and real loopback sockets on port0. A transparent public URL-handler adapter observes real network stream reads for capacity cases; direct HTTP is used for redirects and range behavior. No response bytes or product outcomes are mocked. All resources close in finally blocks, including failing assertions.

| Candidate / desired behavior | Base | Head | Observed result and decision |
| --- | --- | --- | --- |
| Length9, actual body6, EOF: reject incomplete response | FAIL1/1 | FAIL1/1 | Both publish `abcdef` as a successful six-byte cache file. Existing defect: readFullyTo accepts EOF without checking the expected byte count. |
| Length3, actual body6 in one write: do not read beyond three framed bytes | FAIL1/1 | PASS1/1 | Base saves6; head saves3. Smaller requests change the Java stream's closing point. The head follows the advertised framing in this case; this is not proof that the server's intended six-byte artifact was recovered. |
| Length3, send2 bytes, wait for public progress callback, then send4: do not read beyond framing | FAIL1/1 | FAIL1/1 | Base saves6, head saves5. This exact ordering proves size-sensitive behavior without sleeps or scheduler load. Head still over-reads the three-byte framing and discards one of the six bytes actually sent. |
| Length0 with six following bytes: empty framed response | PASS1/1 | PASS1/1 | Both return an empty file. Bytes beyond a zero-length HTTP body are not part of that body; neither version can infer the sender's intended artifact from this alone. |
| Length9, actual6, server holds EOF | FAIL1/1 | FAIL1/1 | Both public futures are pending after the six-byte progress callback. Releasing EOF makes each return a six-byte successful file, then the rejection assertion fails. This shows the waiting mechanism and incomplete-file acceptance, without leaving a hung test. |
| Chunked, no length | PASS1/1 | PASS1/1 | All chunks copied; default array retained. |
| Chunked plus conflicting length1 | PASS1/1 | PASS1/1 | All six decoded bytes copied; the Java transfer decoder takes precedence over Content-Length. Smaller array capacity does not truncate the chunked stream. |
| Unknown, close-delimited response | PASS1/1 | PASS1/1 | All six bytes copied; default retained. |
| Other Content-Encoding | PASS1/1 | PASS1/1 | `custom` coding stays at configured17-byte buffer. Existing PR also checks other coding at the default cap. This is pass-through, not a new decoder. |
| Gzip expansion beyond encoded length | PASS1/1 | PASS1/1 | Exact32768 decoded bytes returned. Existing PR gzip case also passes. Outer gzip allocation coverage remains a gap below. |
| Relative redirect with a length1 redirect body and length6 final body | PASS1/1 | PASS1/1 | Two GETs, second to `/final`, exact final bytes. Downloader uses the final connection's metadata. |
| Range requests3-, response starts2- | PASS1/1 | PASS1/1 | Restarts without Range and without appending the wrong suffix; exact full file returned. The earlier suspicion of missing start validation was refuted by CacheUrl.rangeResOpt and this test. |
| Range starts3-, says end4, but carries3 bytes | FAIL1/1 | FAIL1/1 | Both append and publish six bytes despite the end/body inconsistency. Existing validation checks only the starting-offset prefix. |
| Range starts3-, says total99, carries bytes3-5 only | FAIL1/1 | FAIL1/1 | Both publish a six-byte file despite a stated total99. Existing range-total completeness gap. |
| Exact tiny array6 | FAIL1/1 | PASS1/1 | Base uses1048576; head uses6, with exact bytes and closure. |
| Configured caps5,6,7 with length6 | FAIL1/1 | PASS1/1 | Base fails at cap7 using7 rather than6; head uses5,6,6. |
| Cap1 | PASS1/1 | PASS1/1 | Positive one-byte reads return the full six-byte body. |
| Advertised Int.MaxValue, Int.MaxValue+1,5GiB,Long.MaxValue, cap8192 | PASS1/1 | PASS1/1 | Each real response sends6 bytes then EOF; all observed arrays remain8192. No narrowing overflow or full-length allocation. This is a header-boundary allocation probe, not a real5GiB transfer or a claim that incomplete bodies should succeed. |
| Default HTTP protocol | PASS1/1 | PASS1/1 | Captured request is HTTP/1.1, without HTTP2 upgrade headers. |

The four existing problem classes remain findings even though they do not count as newly introduced patch defects. In particular, the output differences for wrong Content-Length mean callers cannot rely on the pre-patch accidental handling of malformed servers. The copy loop and Java stream behavior need separate response-integrity work at their owning layers. This report does not propose a timeout or retry as their fix.

For the observed understated-length behavior, the mechanism is that Java MeteredStream forwards a bulk read and only then closes when the cumulative count reaches/exceeds its advertised length. Changing the requested read capacity changes how many bytes have been handed back at that point. Its EOF branch closes without treating an early EOF as an error. The fork's unchanged copy loop then publishes those bytes. See [OpenJDK21 MeteredStream](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/sun/net/www/MeteredStream.java#L49). This explains both the differing file sizes and why an early EOF is accepted. The wire framing expectation is specified by [RFC9112 section6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

HTTP/2 applicability is **refuted for the default production transport**, rather than tested by pretending HTTP/1.1 is HTTP/2. CacheUrl opens java.net.URLConnection; OpenJDK's implementation emits HTTP/1.1, also observed on the real server. [OpenJDK21 HttpURLConnection](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/sun/net/www/protocol/http/HttpURLConnection.java#L286) fixes that protocol version. No true HTTP/2 frame or custom HTTP/2 provider test was run; an externally installed handler that implements HTTP/2 is a separate provider contract not represented by this fork's default path. No claim is made about such a provider.

## B: test comprehensiveness

Required reading: complete [PHILOSOPHY.md](https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/PHILOSOPHY.md), complete [architecture/TESTING.md](https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/architecture/TESTING.md), README.md, DEVELOP.md, and the prior branch's RESUME.md, existing-test-edits.md, test-review.md, code-review.md and downstream-gate-independent-review.md. Local reading copies were outside the product checkout. Relevant principles: initially failing public-path reproductions, exact-condition triggers, real local dependencies, resource cleanup, and input/error boundaries. Kotlin script layout and child-JVM annotations do not govern Scala/utest. FileCache requires actual files, so fresh per-case directories with cleanup are appropriate here rather than inventing an in-memory filesystem API.

The PR is strong on its main contract:13 real-server cases, observed buffer sizes via public handlers, exact bytes, stream closure, empty/one-byte/199-byte bodies, identity case folding, caps, unknown/chunked responses, gzip and persisted-prefix resume. Our base run confirms all eight sizing cases still fail; all13 pass on head. The existing helpers have no shared mutable state across scenarios. Boundary assertions depend on counts and lengths, not host speed.

Five permanent-suite gaps remain:

1. **Malformed Content-Length and incomplete responses.** No PR case forces early EOF, trailing bytes, fragmented understated lengths, or a held-short body. Our failing cases show existing wrong acceptance and size-sensitive over-reads.
2. **Malformed Content-Range beyond the start offset.** The PR's correct3-5/6 case cannot catch the existing end/total validation gaps. Our two failing metadata cases cover these omissions; wrong-start restart is a passing control.
3. **Very large advertised lengths and numeric/cap boundaries.** PR payloads are ordinary Int-sized arrays. The Long boundary header probe, plus the configured5/6/7 cross-boundary check, verifies the requested arithmetic without large allocations. These are saved evidence tests, not permanent PR tests.
4. **Redirects.** The PR's general observer returns URLConnection rather than HttpURLConnection, so it does not cover the ordinary HTTP redirect path. Our direct-HTTP redirect control passes both versions but is absent from the PR.
5. **Gzip's outer buffer guarantee.** The PR observes the compressed stream below GZIPInputStream. Its gzip case checks decoded bytes and an upper bound on reads at that lower boundary, not the exact configured size of the decoded transfer array. A wrongly shrunken outer buffer could pass that case. Its corrected title accurately limits the assertion; preserving the encoded-body cap needs an observable decoded-side boundary check, such as logger progress chunks, in addition to the real gzip response.

The prior remote baseline XML was independently parsed:10 runs x13 unique cases =50 pass /80 fail /130; the fixed register is30x13 =390 pass /0 fail /390. This agrees with the lane's eight cases failing10/10 and all13 passing30/30. The prior downstream XML was also independently parsed:446 pass /4 fail /450 across30 iterations. This review did not rerun that gate or infer that the two fewer failures relative to an earlier register were caused solely by buffer sizing.

## C: existing test changes and compliance

No previously merged test is edited: DownloadBufferCapacityTests.scala is introduced within this unmerged PR. Comparison here is against checkpoint `9c137089`; `checkpoint-test-edits.patch` preserves every change.

| Existing checkpoint test edit | Before | After | Judgment |
| --- | --- | --- | --- |
| Resume HEAD requests | `headRequests.get() > 0` | `headRequests.get() == 0` | Changed assertion contract, correcting the old harness; not logical strengthening. doDownload uses GET directly. Base and head both satisfy zero HEAD before reaching the buffer check. |
| Tiny body | Byte/closure/upper-bound checks | Same checks plus exact array length equal to body length | Stronger; fails on base and passes on head. |
| Empty body | Byte/closure/upper-bound checks | Same plus exact array length1 | Stronger; fails on base and passes on head. |
| Configured maximum | Byte/closure/upper-bound checks | Same plus observed array exactly configured maximum | Stronger; already passes base for its over-cap payload. |
| Unknown chunked length | Byte/closure/upper-bound checks | Same plus exact default array observation | Stronger; control passes both. |
| Resume buffer | Prefix/suffix, Range, GET, closure, progress and upper bounds | Same plus exact suffix-length array | Stronger buffer check; corrected full scenario fails base on its array bound and passes head. |
| Gzip case name | `retains the configured maximum while returning decoded bytes` | `with known encoded length returns all decoded bytes` | Title narrowed to what is measured; assertions and waits unchanged. |
| Formatting | Unformatted wrappers/assertions | scalafmt layout, including equivalent synchronized expressions | No changed semantics, wait values, or loops. |

Seven new named scenarios were added to the original six:199-byte POM, one byte, lowercase identity, mixed-case identity, other coding, default-size boundaries, and unknown-length identity. They do not replace or remove original checks. Await30.seconds and executor termination10seconds are unchanged. No iteration count was reduced. No disabled/skipped tests, retry mixins, mocks, reflection, visibility increases, or JVM tuning flags were introduced. The stream adapters forward real connections and record reads; they do not supply predetermined client outcomes.

## Scope completion and cleanup

All requested candidate dimensions were tested or refuted with evidence. The HTTP/2 limit is stated above; no genuine HTTP/2 frames were exercised. No full suite, publication, deployment or CI watch was part of this report-only job. The five coverage gaps and four existing response-handling problem classes were reported, not silently fixed or left as claimed passes. Initial harness compilation failure is preserved and excluded from body-attempt counts.

All probe JVMs, local server threads and controllers terminated. The final numeric PID/start-time ownership audit and required ps/grep check found no live owned process, owned zombie, or unrecorded matching process; raw process command lines were neither saved nor committed. Cgroup oom_kill stayed2 throughout, and no lane command exited137. The product checkout is clean on the original PR head. Findings, tests, patches, logs, XML and audits are on the evidence branch after the required scan; no build outputs are committed.

2026-10-22T22:10:17.095247+00:00 — OBSERVED: Prior checkpoint local and remote bothd379a3fd7c9b13bd054ffa69a060e49f4b5e535e after its scan and push. OBSERVED: Final cleanup finds14 recorded child identities, zero live owned processes, zero owned zombies and zero unrecorded matching processes; memory.events oom_kill remains2. OBSERVED: PR is freshly verified OPEN at2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8; remote main remains7434805e35d9941f840c6e8c914f7d53fe852e10. OBSERVED: No Addendum heading is present, and no product or existing test source is modified in the final checkout. INFER: The only remaining step is the final evidence scan/push/remote-equality verification.
