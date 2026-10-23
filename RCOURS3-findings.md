# RCOURS3 review findings

PR: https://github.com/CodexCoder21Organization/coursier/pull/7

## Plan

- [completed] Read required documentation, fresh clone README/instructions, prior remote evidence, confirm head and rebase locally; compare main for overlap.
- [completed] Trace response framing, decoding, range, cleanup, streaming progress, redirects, HEAD and cache ownership; write invariant and boundary register.
- [completed] Run targeted single-attempt tests for prior findings and new candidate defects on main and head using real local servers.
- [completed] Review coverage and compliance, secret-scan and push evidence at milestones, verify remote SHA, finish three verdicts and process cleanup.

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

2026-10-23 03:47:35 UTC — OBSERVED: Identical main comparison completed102first attempts:35pass/67fail; the seven independent cases pass2/fail5. OBSERVED: Empty encoding and empty identity-list elements pass main but fail head, confirming one introduced list-parsing defect with two reproductions. OBSERVED: Repeated encoding fields, incomplete second gzip member and streaming gzip progress fail both main and head; nested incomplete gzip through deflate fails main and passes head. OBSERVED: Evidence checkpointfc8741e2ec5083d1ae0df71365db31b901736516 verified local=remote. OBSERVED: No Addendum headings. INFER: Next checkpoint is a three-case decoder-ending check against both versions, then line-specific verdicts; repeated-field failures are one inherited defect and should not be counted twice.

2026-10-23 03:49:38 UTC — OBSERVED: Outer-decoder register has0pass/3fail on main and1pass/2fail on head, each one attempt. OBSERVED: Head accepts corrupt or incomplete outer gzip trailers when inner deflate reaches EOF; the outer deflate/inner gzip checksum case correctly rejects on head. INFER: There are five defect groups: one introduced encoding-list rejection and four inherited acceptance/progress groups. OBSERVED: The original seven RCOURS2 findings all have passing head scenarios in the95-case register. OBSERVED: No Addendum headings, no product edits, and no check re-request or remote build. INFER: Next checkpoint is final coverage/compliance write-up, scanned evidence push and process cleanup.

## Final verdicts

**A: REQUEST CHANGES — 5 defect groups: 1 introduced, 4 inherited. B: GAPS — 5 groups. C: PASS.**

Reviewed https://github.com/CodexCoder21Organization/coursier/pull/7 while OPEN at https://github.com/CodexCoder21Organization/coursier/commit/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367. The diff is against `git merge-base origin/main <head>` = https://github.com/CodexCoder21Organization/coursier/commit/f7ac8c67dbe757d9129cfa453a37f2867bd1272d. Local rebase leaves head unchanged. Fresh remote verification confirms main still equals that merge-base; main includes the buffer-sizing prerequisite and does not supersede this PR. Product remote `fix/FCOURS2-download-length-integrity` remains ac74fa03ab018e66a67f293dbae7a4fe3a7bd367. Local `review/RCOURS3-main` at 1752812f12ed2363ecccfb05ad171b008d6d1229 differs from main only by four added test files; it was not pushed.

### A1 — P2, introduced: correct empty encoding lists are rejected

[Downloader.scala lines 269–278](https://github.com/CodexCoder21Organization/coursier/blob/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L269).

A real server sends `200`, `Content-Length: 6`, `Content-Encoding:` and `abcdef`. Main publishes the complete six-byte body. Head returns `Unsupported Content-Encoding '' from <artifact URL>: expected identity, gzip, or deflate` and removes the temporary file. `Content-Encoding: , identity, ,` has the same new rejection. Splitting with a negative limit retains empty strings, and the unsupported-coding check treats each as a coding name.

Content-Encoding permits a zero-member list, and recipients must ignore a reasonable number of empty list elements. This is a correct-response regression, not a request to accept unknown coding names. See [RFC 9110 section 8.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.4) and [section 5.6.1.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.1.2). Ignore empty members before selecting/validating decoders. The PR's new “unknown and empty encodings” case currently asserts the wrong behavior for the empty value; separate that value from unsupported names.

Proof: `Rcours3ReviewTests.scala:405` and `:410`, both PASS on main and FAIL on head, one attempt each; the success assertion at line 342 catches both errors. Exact failures and full values are in `head-review.xml`.

### A2 — P2, inherited: repeated encoding fields use only the final field

[Downloader.scala line 269](https://github.com/CodexCoder21Organization/coursier/blob/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L269).

For two `Content-Encoding: gzip` fields and `gzip(gzip(abcdef))`, both versions decode once and publish gzip bytes instead of `abcdef`. A preceding `Content-Encoding: custom` followed by `Content-Encoding: identity` is also accepted on both versions, even though head claims unsupported codings are rejected. `getContentEncoding` exposes just one field; the list-processing code never sees the earlier field. Combine the field lines in their original order before decoding or rejecting. The field is list-valued under [RFC 9110 section 5.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.2).

Proof: `Rcours3ReviewTests.scala:415` and `:458` FAIL on main and head, one attempt each. This is one inherited header-selection defect with two observable consequences, not two findings. Both final cache files exist in the saved observations.

### A3 — P2, inherited: a cut-off later gzip member can publish only the earlier member

[Downloader.scala decoder construction](https://github.com/CodexCoder21Organization/coursier/blob/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L326) and [finish call](https://github.com/CodexCoder21Organization/coursier/blob/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L360).

The server sends a complete gzip member for `abc` plus the first five bytes of a second gzip member for `def`; Content-Length accurately describes those response bytes. Both versions return success and cache only `abc`. Wire-length counting cannot establish that every gzip member is complete. OpenJDK's `GZIPInputStream.readTrailer` treats an IOException while reading the next header as overall EOF; the new raw finish step does not recover that lost decoder error. Source inspected: [OpenJDK 21 GZIPInputStream](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/zip/GZIPInputStream.java), saved as `jdk21-GZIPInputStream.java`. Validate the complete encoded representation rather than treating this decoder's EOF as sufficient.

Proof: `Rcours3ReviewTests.scala:422` FAILS on both versions at the rejection assertion, one attempt each; XML records `Right(Array(97,98,99))` and a final cache entry. The valid separate-member test in the PR passes, but does not cover an incomplete subsequent header.

### A4 — P2, inherited: progress inside a gzip member waits for its next compressed fragment

[Downloader.scala outer buffer](https://github.com/CodexCoder21Organization/coursier/blob/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L337).

The real server uses `GZIPOutputStream(..., true)` to send a chunk containing a sync-flushed `abc`, then waits for three decoded bytes of progress before sending the remaining compressed bytes. Both versions fail the unchanged 30-second progress gate. The bytes are decodable, but `BufferedInputStream.read` tries another read because `InflaterInputStream.available` reports one before inflater EOF, so the caller cannot observe the first fragment until the next arrives. This is a forced dependency ordering, not a machine-speed threshold. The PR's close-delimited identity case and its complete-member boundary case both pass; neither exercises a fragment within one compressed member.

Proof: `Rcours3ReviewTests.scala:439` FAILS on main and head, once each, with `Timed out waiting for download progress to reach 3`; complete failure records are in the corresponding XML. Inspecting [BufferedInputStream](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/io/BufferedInputStream.java) and [InflaterInputStream](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/zip/InflaterInputStream.java) confirms the extra-read mechanism. Preserve streaming progress without requiring the subsequent compressed fragment. Teardown releases the response and closes all resources even on this failure.

### A5 — P2, inherited acceptance: inner decoder EOF can bypass an outer gzip trailer

[Downloader.scala lines 323–361](https://github.com/CodexCoder21Organization/coursier/blob/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L323).

For `Content-Encoding: deflate, gzip`, the response body is `gzip(deflate(abcdef))`. Corrupt the final gzip trailer byte, or remove four trailer bytes and set Content-Length to the actual shortened wire length. Head returns success and caches decoded `abcdef` in both cases. Once the inner deflate stream ends, `response.finish` drains the raw response instead of finishing each decoder, and closing a gzip stream does not validate its unread trailer. The complete decoded bytes do not make the incomplete or invalid encoded representation acceptable.

Proof: `Rcours3DecoderEndTests.scala:405` and `:425` FAIL on both main and head, one attempt each. Classification is inherited **acceptance**, not a claim that main already supports stacked decoding: main stores the raw representation, while head adds decoding but still accepts these invalid responses. The reverse outer-deflate/inner-gzip corrupt-checksum test (`:415`) fails main and passes head. Finish and validate every decoding layer before publication, then validate response framing.

### All seven RCOURS2 findings are fixed on this head

| Prior finding | Current head proof in DownloadResponseFramingTests | Result |
|---|---|---|
| Mixed-case range unit rejection | valid mixed case range unit completes the artifact | PASS |
| Chunked range published without closing chunk | chunked range must consume the closing chunk before publishing; pending-state latch case | PASS |
| Data beyond range | chunked range rejects data beyond its stated range | PASS |
| Unsolicited 304 | unsolicited 304 cannot publish an empty artifact | PASS |
| Temporary bytes after cut-off | cut off chunked response removes temporary bytes; cut off gzip response removes temporary bytes | PASS, both |
| Incomplete deflate | short other encoded response cannot publish partial bytes | PASS |
| Mixed-case gzip | correct mixed case gzip decodes the body | PASS |

These passes are from this review's single-attempt run, not copied from FCOURS4's claimed gate. No original finding remains open in its original reproduced form.

### Remaining requested boundary audit

| Boundary | Evidence and conclusion |
|---|---|
| Fixed identity bodies, empty, short, huge lengths | Full-text expected/received/URL errors; no final or temporary file after rejection. All corresponding head tests pass. |
| Bytes after a fixed HTTP Content-Length | Reads remain inside HTTP framing, including zero-length bodies. Later network bytes are outside this response, not artifact bytes; valid framing is not wrongly rejected. |
| Chunked transfer | Closing chunk is consumed even after range count or codec EOF; conflicting Content-Length is ignored; short and over-range bodies rejected. Current head cases pass. |
| gzip/deflate lengths | Counts refer to response bytes, not expanded bytes. Valid compressed bodies larger after decoding pass; unsupported names reject descriptively. A1–A3/A5 describe the remaining encoding limitations. |
| HEAD | Public ConnectionBuilder HEAD metadata test passes with Content-Length 6 and no body; byte-count validation is confined to artifact download. |
| 304/204/205 | GET cannot publish no-body responses; saved prefixes are removed. Current tests pass. |
| Redirects and other success statuses | Final response length and original artifact URL preserved; body-bearing unfollowed 301/302/303/307/308 and 201/202/203/207 remain accepted. Current tests pass. |
| HTTP error paths | Exact existing 500-response message is preserved; encoded output-file errors retain their native cause rather than becoming decoding errors. Current tests pass. |
| Resume | Valid matching identity 206 appends; 200 replaces prefix; missing/invalid/conflicting ranges and encoded partial responses reject and clean up. Mixed-case units accepted; pending chunk terminator holds publication. Current tests pass. |
| No length | Close-delimited nonempty/empty bodies read to EOF; there is no separate expected count without metadata. Identity fragment progress passes. gzip in-member progress remains A4. |
| Buffer sizing | All 14 head cases pass. Maximum configured buffer bounds, one-byte empty minimum, exact zero-byte read, tiny/large/resumed bodies, encoded bodies and stream closure remain tested. |
| Same artifact concurrency | Two callers are synchronized through observed watching-owner progress. Correct body produces one GET and identical complete results; short bodies produce two errors, no final or temporary bytes, and release ownership. No introduced lock regression found. |

## B — GAPS (5 groups)

The existing 81 length/framing cases plus 14 buffer cases all pass and use real loopback HTTP servers, public FileCache/ConnectionBuilder APIs, fresh temporary directories, assigned ports and finally-based cleanup. Error checks compare complete messages and values; the custom protocol in buffer tests only records real stream operations. FileCache's real filesystem locks/atomic moves require actual isolated files. Plain utest executes each case once with no retry mixin.

The six coverage groups identified by RCOURS2 are present and pass in their original forms. Five additional groups are missing or encode a wrong expectation: (1) empty/empty-element coding lists, (2) repeated coding field lines, (3) incomplete subsequent gzip headers, (4) compressed fragments within a member before decoder EOF, and (5) invalid outer coding endings after inner decoder EOF. The ten independent cases cover those groups and two adjacent refutations (nested incomplete inner gzip and outer-deflate checksum both reject correctly on head). Add those assertions to the product suite after fixing the observed behavior; do not replace the passing old boundaries.

## C — PASS

No reflection, Mockito, mocks, test-only visibility increases, skipped/disabled/deleted tests, raised established timeouts, reduced iteration counts, or weaker existing assertions were found in the reviewed diff. New errors retain URL, actual metadata and expected behavior/counts. The saved PR description opens with the prior short-response cache failure and the user's fix-round request, before the implementation details, and inventories the existing-test change.

**Existing-test edit inventory:** `DownloadBufferCapacityTests.scala` changes “other content encodings retain the default maximum” (three raw bytes, advertised `custom`, expect those raw bytes) to “deflate content encoding retains the default maximum” (real deflate bytes, advertise actual encoded length, expect decoded bytes). Every maximum-buffer, exact configured-buffer observation, stream-close and exact-body assertion remains. This preserves the buffer-sizing purpose; unsupported raw coding passthrough was a fixture expectation, not a documented promised decoding contract. The new rejection of unsupported names is explicit in README/description and has separate tests. The identical revised buffer case fails on main's implementation and passes on head, so it does not merely remove a failing check. The other changed buffer case is an added zero-byte-read test, not an edit to an old assertion. No diagnostic golden literals changed. Review RCOURS3 itself edits no existing test or product file; main comparison adds renamed/copied test sources only.

## Gates, attempts and reproducibility

| Invocation | Cases | Passed | Failed | Attempts / retries |
|---|---:|---:|---:|---|
| head-existing | 95 | 95 | 0 | 95 first attempts / 0 retries |
| head-review | 7 | 1 | 6 | 7 first attempts / 0 retries |
| main-comparison (same 81 PR cases, renamed 14 buffer cases, 7 independent) | 102 | 35 | 67 | 102 first attempts / 0 retries |
| main-decoder-ends | 3 | 0 | 3 | 3 first attempts / 0 retries |
| head-decoder-ends | 3 | 1 | 2 | 3 first attempts / 0 retries |

Totals: head **97 pass / 8 fail in 105 first attempts**; main **35 pass / 70 fail in 105 first attempts**. No errors or skips. Main's PR-case failures include intentionally new validation/error-text expectations; those failures alone are not attributed as new defects. Classification uses the unchanged independent behavior assertions.

Each invocation used `./mill --no-server -j1 'cache.jvm[2.13.16].test.testOnly' <named suites>` through saved `run-command.py`, which fetches/rebases and records `free -m` before starting one command. Only one test JVM was active per invocation; no multi-worker/full-suite slot was needed. No remote runs or full suite were submitted. FCOURS4's full-module 187/187 result is prior evidence only and is not claimed as rerun here. XML files, qualified execution events and the plain utest Executor/runner source establish the attempt counts; repeated failed-case summary printouts are not second attempts.

Full test failure details are in the saved XML/log files, rather than copied into the conversation. `reviewed-diff.patch` records the exact reviewed main-to-head diff; the two Rcours3 source files reproduce each new candidate unchanged on both versions. README, DEVELOP, full PHILOSOPHY/TESTING, prior RCOURS2/FCOURS4 findings and the decoder/stream source cited above were read. No Addendum headings were present at the final checkpoint.

## Scope and cleanup

Report-only review is complete; no requested review item is silently deferred. Findings identify needed product fixes; no fix, product push, PR comment/edit, merge, queue entry, publication, deployment, handoff command or check request was made. All seven old findings were verified fixed; new findings retain their failing assertions in evidence. No full suite was required by this review brief, and none was run.

All tracked command sessions have exited. The final process audit checks recorded PIDs with start times and the required `ps -eo pid,args -ww | grep -v grep` output without saving raw argument text; `process-cleanup.json` reports zero live tracked processes. No agents or scheduled loops were started.

2026-10-23 03:53:17 UTC — OBSERVED: Final audit matches105unique case names across head/main, with one attempt per version and no errors/skips/retries; head97pass/8fail, main35pass/70fail. OBSERVED: Fresh remote main/product SHAs remain f7ac8c67dbe757d9129cfa453a37f2867bd1272d/ac74fa03ab018e66a67f293dbae7a4fe3a7bd367 and PR remains OPEN. OBSERVED: All command sessions have exited, process-cleanup.json reports zero live tracked processes, and no Addendum headings exist. INFER: The review is complete; next checkpoint is scanned final evidence push and local/remoteSHAverification.
