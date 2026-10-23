# RCOURS2 review findings

PR: https://github.com/CodexCoder21Organization/coursier/pull/7

## Plan
- [x] Locate fork, confirm head, read brief and required documentation, rebase review branch on current main.
- [x] Inspect changed response handling, cache publication and lock ownership; enumerate candidate defects.
- [x] Write public-API local-server reproductions, run each once on main and head, audit attempts.
- [x] Review coverage and compliance; push scanned evidence and verify remote SHA; clean up processes.

## Reading
Read repository README.md and DEVELOP.md (no root AGENTS.md). Read DocumentationRepository PHILOSOPHY.md and architecture/TESTING.md, focusing on observable public API behavior, real local servers, fail-first tests, full error assertions, isolated ports/storage, deterministic triggers, never hiding failures, and refuted hypotheses. The repository uses Scala/Mill, not kompile; single-attempt utest execution will follow the prior evidence launcher after inspection. No product source or existing test will be edited.

## Initial state
2026-10-23 01:27:36 UTC — OBSERVED: PR OPEN; head 7ab7bfffb49123f9f73c45791e41b6c1ae056e7a confirmed. OBSERVED: Fresh clone at scratchpad/workspace/RCOURS2-coursier-20261020; review/RCOURS2 rebase onto current origin/main f7ac8c67dbe757d9129cfa453a37f2867bd1272d reports up to date and leaves HEAD unchanged. OBSERVED: Main includes buffer sizing via https://github.com/CodexCoder21Organization/coursier/commit/f7ac8c67dbe757d9129cfa453a37f2867bd1272d; merge-base equals that main commit. INFER: The added response length and range checks are not superseded. OBSERVED: Full brief read; no Addendum headings present. INFER: Next checkpoint is the response-path audit and reproducible targeted test harness.

2026-10-23 01:29:05 UTC — OBSERVED: First evidence checkpoint pushed; local and remote SHA both 60a979911eba5ce833c072592655bc4a911175ec. OBSERVED: No Addendum headings. OBSERVED: Head has 43 new length-integrity cases plus 14 buffer-capacity cases, using loopback servers and full-text length/range error assertions. INFER: Candidate boundaries not covered are no-body statuses, incomplete chunk framing, non-identity encodings, case variation in range units, and same-artifact concurrency on rejection. OBSERVED: Targeted original suites running once through plain utest/Mill with one worker. INFER: Next checkpoint is compiling independent boundary reproductions and classifying main versus head behavior.

2026-10-23 01:30:47 UTC — OBSERVED: Authored Rcours2BoundaryTests.scala as a new evidence-only suite, with 13 public-API scenarios against real loopback servers. OBSERVED: Candidate range-unit mismatch is supported by RFC 9110 section 14.1 (https://www.rfc-editor.org/rfc/rfc9110.html#section-14.1), which defines case-insensitive range-unit names. INFER: New lowercase-only regex can reject a correct ByTeS response that main restarts successfully; execution will decide. OBSERVED: Initial targeted Mill invocation is compiling from the fresh checkout; no test attempts have completed yet. OBSERVED: No existing tests or assertions edited; no Addendum headings. INFER: Next checkpoint is the first 57-case result, then the 13 independent scenarios on head and unchanged main production sources.

2026-10-23 01:32:14 UTC — OBSERVED: Existing targeted head suites completed 57/57 passing (43 integrity, 14 buffer), in a single Mill invocation using plain utest 0.9.1; XML has 57 unique cases and no failures/errors/skips. OBSERVED: Evidence test-source checkpoint local and remote SHAs both c56386cfdb8a56c5ed4578bb633aa36340cc7514. OBSERVED: Independent suite now includes an observed watching-owner callback before releasing the owner response, plus HEAD metadata checks; execution on head is in progress. OBSERVED: No Addendum headings. INFER: Next checkpoint is head-versus-main results and line-specific review findings.

2026-10-23 01:33:06 UTC — OBSERVED: Head boundary suite completed 13 first attempts: 7 pass, 6 fail. OBSERVED: Failures are valid mixed-case range rejection, unsolicited 304 cached empty, retained chunked/gzip temporary files, short deflate bytes cached, and mixed-case GZip stored without decoding. OBSERVED: Concurrency on short responses, same-file success, HEAD metadata, empty no-length bodies and chunked range framing pass. OBSERVED: Baseline setup first stopped before any test execution because rebase rejects unstaged production-source substitutions; corrected by creating local review/RCOURS2-main on origin/main with only the identical review test sources committed. OBSERVED: Main-comparison invocation is now running; no main production changes, no test retries. INFER: Temporary bytes after generic IO failures may be intentional resume behavior; distinguish that inherited policy from the new explicit length/range rejection cleanup rather than claiming every retained prefix is itself wrong. INFER: Next checkpoint is main results, attempt audit, and final classification.

2026-10-23 01:34:11 UTC — OBSERVED: Main comparison completed 70 unique cases once: original 57 have 24 pass/33 fail; boundary 13 have 6 pass/7 fail. OBSERVED: Valid mixed-case range passes on main and fails on head, confirming a new rejection. OBSERVED: Five original boundary failures also fail on main; short chunked ranges and concurrent short-body downloads fail on main and pass on head. OBSERVED: Head-results checkpoint local and remote both cfc80df0e9e4e25fed5d77373d52505af54271ab. INFER: A second candidate exists where Content-Range provides an expected count but chunked framing still owns the message ending; stopping at the count may bypass a missing final chunk or excess range data. OBSERVED: Two new candidate tests are authored and running once on head; main will run identical sources next. OBSERVED: No Addendum headings. INFER: Next checkpoint is this framing comparison and the final response/coverage matrix.

## Review status

DONE — fresh-context report-only review of https://github.com/CodexCoder21Organization/coursier/pull/7. No delegated work, blockers, paused items, landing actions or scheduled status loops. Holds audit: none; the independent candidates were tested in sequence to follow the user's one-command resource rule. Owner direction is the report-only brief; no new direction arrived.

The PR bounds fixed-length identity reads and reports early EOF before moving a temporary file into the cache. It validates resumed range metadata and removes temporary files on its explicit validation errors. Encoded responses keep their configured buffer and existing decoder behavior, while two new range paths need correction as detailed below.

Lessons: a response data count does not replace its transfer framing; protocol-name case rules belong in boundary tests; compare identical test sources against main to separate regressions from inherited behavior. Next steps: none for the review; the PR owner can use the two introduced reproductions to revise the change.

## Final review — A: REQUEST CHANGES

Seven findings/observations: **two introduced regressions**, five inherited behaviors. The two introduced regressions are the reasons to request changes on this PR. No fix was made and no PR comment or check request was sent.

### A1 — P2, introduced: valid mixed-case range units are rejected

Location: [CacheUrl.scala line 250](https://github.com/CodexCoder21Organization/coursier/blob/7ab7bfffb49123f9f73c45791e41b6c1ae056e7a/modules/cache/jvm/src/main/scala/coursier/cache/CacheUrl.scala#L250), used at lines 265–278.

With a saved `abc` prefix, the real server returns `206`, `Content-Range: ByTeS 3-5/6`, `Content-Length: 3`, and `def`. Main completes `abcdef` by restarting with a full response; the head returns `Invalid Content-Range 'ByTeS 3-5/6' from <URL>: expected bytes starting at 3 and ending at the complete response length` and removes the saved prefix. The regex compares the range-unit spelling case-sensitively. HTTP defines range-unit names as case-insensitive: [RFC 9110 section 14.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-14.1).

Proof: `Rcours2BoundaryTests.valid mixed case range unit completes the artifact` passes on main and fails on head. The existing lowercase range cases pass on head. Accept the case variants while keeping the numeric checks.

### A2 — P2, introduced: chunked range downloads publish before validating the message ending

Locations: [Downloader.scala line 264](https://github.com/CodexCoder21Organization/coursier/blob/7ab7bfffb49123f9f73c45791e41b6c1ae056e7a/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L264) and [line 828](https://github.com/CodexCoder21Organization/coursier/blob/7ab7bfffb49123f9f73c45791e41b6c1ae056e7a/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L828).

A saved `abc` prefix receives `206`, `Transfer-Encoding: chunked`, `Content-Range: bytes 3-5/6`, no Content-Length, and wire bytes `3\r\ndef\r\n` followed by connection close. There is no final zero chunk. Main reads the chunk framing to its ending, returns an IO error and publishes nothing. The head treats the Content-Range count as a response boundary, stops immediately after `def`, and publishes `abcdef` as success. This bypasses the chunked reader's incomplete-message error. Content-Range is an expected data count; it does not replace chunked message framing.

Proof: `Rcours2ChunkedRangeTests.chunked range must consume the closing chunk before publishing` passes on main and fails on head. Both use the same source and real raw HTTP server. Continue validating the actual chunked message ending before publication and reject any data beyond the range count. Preserve bounded reads for fixed Content-Length responses, where later bytes are outside that response body.

### Inherited findings and limitations — no new rejection attributed to this PR

| ID | Candidate test / observable behavior | Main | Head | Classification |
|---|---|---|---|---|
| A3 | `chunked range rejects data beyond its stated range`: range is 3-5/6 but the decoded chunk contains `defXYZ` | Reject assertion fails: publishes 9-byte `abcdefXYZ` | Reject assertion fails: publishes 6-byte `abcdef` | Inherited acceptance of an inconsistent range body; head changes the output to the range-sized prefix. Related to A2, but the acceptance already exists on main. |
| A4 | `unsolicited 304 cannot publish an empty artifact`: GET receives 304 with no length or body | Reject assertion fails; empty final cache entry | Same | Inherited status handling. A 304 is not an artifact body for this unconditional GET. |
| A5 | `cut off chunked response removes temporary bytes` and `cut off gzip response removes temporary bytes` | Return error, no final cache entry, six temporary decoded bytes retained | Same | Inherited IO-error resume policy; the new catch removes temporary files only for ArtifactError.DownloadError thrown directly in doDownload. Generic IO exceptions are wrapped outside that catch. Retention of a resumable prefix is not itself a new bug; the requested “always removed on rejection” statement is false outside the explicit new validation errors. |
| A6 | `short other encoded response cannot publish partial bytes`: incomplete deflate body with its original Content-Length | Reject assertion fails; partial encoded bytes cached | Same | Inherited unsupported-encoding handling. Only exact lowercase gzip is decoded; other encodings take the raw-stream path. The PR intentionally exempts non-identity encoding from decoded-byte length comparison. |
| A7 | `correct mixed case gzip decodes the body`: valid `Content-Encoding: GZip` | Body assertion fails; compressed bytes cached | Same | Inherited case-sensitive decoder selection at Downloader.scala line 289. Content codings are case-insensitive under [RFC 9110 section 8.4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.4.1). |

All candidates were written as assertions for the required behavior before running them on both versions. None was “fixed” or relaxed to produce a passing result. Expected failures remain present in the saved XML and logs.

### Boundary and ownership audit

| Boundary / resource | Evidence and result |
|---|---|
| Fixed identity length: early EOF, empty positive length, repeated short reads, huge lengths | Existing head tests pass; full-text errors include original artifact URL, expected bytes and received bytes. Explicit validation errors remove the temporary file before releasing download ownership. |
| Fixed identity length: extra network bytes, zero length | Head reads only the framed body, including zero bytes for length zero. Extra bytes after fixed HTTP framing are not artifact body bytes and should not be cached. This is bounded framing, not an explicit “over-long body” error. |
| Chunked 200, including conflicting Content-Length | Existing valid chunked test passes. Cut-off chunk framing returns an error on both versions; inherited temporary retention recorded in A5. |
| Chunked 206 | Valid range and short range tests pass on head; missing closing chunk exposes A2 and extra data exposes A3. |
| gzip with a valid Content-Length | Existing valid gzip test passes with decoded output larger than the wire length; configured outer buffer remains in use. No false wire-versus-decoded byte comparison introduced. |
| Other encodings and coding case | Valid deflate transport is accepted unchanged; existing decoding limitations are A6/A7. |
| HEAD | Independent public ConnectionBuilder HEAD test passes on both versions with Content-Length 6 and no body. HEAD metadata does not use doDownload's byte validator. |
| 304 | Unconditional GET/no-length case is A4. A positive declared length would cause the new early-EOF check to reject rather than cache an empty artifact. |
| Redirects | Both existing redirect tests pass: final-response framing is used, and early-EOF errors retain the original artifact URL. Redirect connections and connections rejected while validating ranges are closed by CacheUrl's error path. |
| Range/resume | Existing saved-prefix, full-200 replacement, missing/invalid/conflicting ranges and numeric boundaries pass on head. Range-unit case regression is A1. |
| No length / zero length | Close-delimited nonempty and empty cases pass; without any declared length or range total there is no independent expected count to compare. |
| Buffer sizing from the earlier PR | All 14 buffer tests pass on head. Main passes the original 13 and fails the newly added exact zero-byte-read scenario. No large buffer regression found. |
| Same-file ownership | Both independent concurrent tests wait for `downloadLength(..., watching=true)` from the second caller before releasing the owner's response. Head rejects both short-download callers without publishing or retaining temporary bytes, and shares one GET for correct data. Main fails the short-body case and passes correct sharing. |
| Temporary-file lifecycle | Download owner creates/writes/promotes the temporary file while holding canonical-file ownership and file lock. Structural changes use the structure lock. New length/range/buffer errors delete the temporary file before returning Left; generic IO errors follow the inherited retention path. |
| Re-entry / caller mutation / persisted state | No new callback re-entry or mutable return-value change is introduced. Logger progress still occurs under existing download ownership. Saved-prefix tests exercise re-entry from persisted partial state; simultaneous callers exercise ownership and release. |

## B: GAPS (6 coverage groups)

The added integrity tests use real loopback HTTP servers on port 0, isolated temporary cache directories, actual FileCache public calls, closed sockets/executors, and full-text assertions for the newly added error messages. The buffer suite instruments a real delegated HTTP stream through the existing custom-handler API; it does not replace the HTTP server with a mock. Temporary real files are appropriate here because FileCache's file/OS-lock API is the behavior being exercised.

The PR's tests do not cover these six groups adequately:

1. Valid case variants of Content-Range units (A1).
2. Chunked range completion and excess data after the range count (A2/A3); ordinary chunked success does not cover this combination.
3. No-body statuses and HEAD metadata, especially unsolicited 304 publication (A4).
4. Other encodings and case variants of gzip (A6/A7).
5. Cleanup after errors from the real chunked/gzip stream rather than direct new validation exceptions (A5).
6. Concurrent same-file callers while the new length error is reached and ownership is released; existing general ownership tests do not exercise that combined path.

The evidence suites cover every group above. Successful HEAD/concurrency probes demonstrate correct current behavior but are still absent from this PR's new regression register. This verdict follows [architecture/TESTING.md](https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/architecture/TESTING.md): test observable public behavior, use real dependencies, force the condition, and retain failing evidence.

## C: PASS

Reviewed the merge-base diff, added test code and PR body. No reflection, Mockito, mocks, raised timeouts, reduced iterations, deleted/disabled tests, relaxed assertions, visibility increases for tests, JVM tuning, or retry wrappers were added by this PR. Newly added errors state the URL and relevant values; incomplete-body errors state both expected and actual counts and tests assert their full text. The PR description opens with the observed short/over-read behavior found during review of https://github.com/CodexCoder21Organization/coursier/pull/6, before explaining the implementation.

Existing-test edit inventory: this PR adds exactly one new eight-line scenario to `DownloadBufferCapacityTests.scala`, requiring a one-byte array and exactly one zero-byte read for an empty body. All existing test bodies/assertions/timeouts/iteration counts remain unchanged. `DownloadLengthIntegrityTests.scala` is new (43 scenarios). This review edits no existing tests. For the main comparison only, the exact head test sources, including that added eight-line scenario, were committed to a local test-only branch; the production-source diff against origin/main was empty. No existing assertion was restructured or loosened.

## Gates and exact attempts

| Invocation | Pass | Fail | First attempts | Retries | Skips |
|---|---:|---:|---:|---:|---:|
| Head original length + buffer suites | 57 | 0 | 57 | 0 | 0 |
| Head independent boundary suite | 7 | 6 | 13 | 0 | 0 |
| Main original + independent boundary suites | 30 | 40 | 70 | 0 | 0 |
| Head independent chunked-range suite | 0 | 2 | 2 | 0 | 0 |
| Main independent chunked-range suite | 1 | 1 | 2 | 0 | 0 |
| Total | 95 | 49 | 144 | 0 | 0 |

Main's original suites alone are 24 pass/33 fail in 57 first attempts. The 15 independent scenarios total head 7 pass/8 fail versus main 7 pass/8 fail, with different failing cases: the head fixes short-range and concurrent-short-body cases but adds mixed-case-range rejection and missing-closing-chunk acceptance.

Attempt proof: `attempt-audit.json`, XML, and logs were inspected; suites extend plain TestSuite and do not override utestWrap or use TestSuite.Retries. The pinned utest 0.9.1 Executor runs each body once; Task.execute invokes the suite once, and BaseRunner invokes TestRunner.runAsync once. BaseRunner records failure strings and MasterRunner.done renders them again in the large main failure report: all 40 repeated lines have identical text and durations, while each of the 12 server-backed boundary observations appears exactly once (HEAD has no observation line). This is repeated reporting, not a second attempt. The audit records terminal rendering counts honestly instead of interpreting those duplicate failure renderings as retries. Source copies are saved alongside the audit.

One baseline launcher setup stopped before building or executing any test because rebase refused unstaged source substitutions. No test was retried; the subsequent test-only baseline branch preserved the required rebase gate and exact production sources.

Only targeted suites ran, one build/test command at a time with Mill `--no-server -j1`; no step launched more than two test JVMs. No full suite, remote build, CI check request, or full-suite slot was used. The earlier evidence's 149/149 cache-module claim was read as provenance, not counted as a run of this review. Each command checked free memory; no low-memory wait, exit 137, timeout deadline, or retry occurred.

## Branches, main overlap, and completion

- PR remains OPEN, remote `fix/FCOURS2-download-length-integrity` = `7ab7bfffb49123f9f73c45791e41b6c1ae056e7a`; it was never pushed or changed by this review.
- Remote `main` = `f7ac8c67dbe757d9129cfa453a37f2867bd1272d`. It contains the earlier buffer-sizing change; the PR's length/range checks are not superseded. The rebase left the head unchanged, and the reviewed diff is from that merge-base.
- Local `review/RCOURS2` ends at the exact PR head with a clean worktree. Local `review/RCOURS2-main` is a test-only comparison branch ending at `61e46d0ab33122a367a65705eef71779a5f8cb03`; it was not pushed. Its complete test sources and baseline definition are preserved on the evidence branch.
- Evidence branch: https://github.com/CodexCoder21Organization/coursier/tree/wip/RCOURS2-evidence-20261023 . Every evidence push followed the requested tree scan. No scan hits were found.
- Verified checkpoints (local SHA = ls-remote SHA): `60a979911eba5ce833c072592655bc4a911175ec`, `c56386cfdb8a56c5ed4578bb633aa36340cc7514`, `cfc80df0e9e4e25fed5d77373d52505af54271ab`, `fe8d0bbb54be546e8d5c13a3194df098a1480b29`. Final evidence verification is appended after the final content commit.
- Process cleanup: all five test command sessions completed; recorded process IDs were checked with start times, and none remains alive. The required final `ps -eo pid,args -ww | grep -v grep` check ran, with raw output retained only outside evidence. `process-cleanup.json` contains only counts and recorded-ID results, with no process command lines.
- No pending review item remains. Report-only scope means no product fix, PR-branch push, merge, queue action, publish, deploy, check request or handoff command was performed. No new PR was created for evidence.

2026-10-23 01:37:32 UTC — OBSERVED: All requested review boundaries have test or code-path evidence, both introduced candidates are proved on main and head, and the attempt audit covers 144 first attempts with zero retries. OBSERVED: No Addendum headings were present at the last checkpoint. OBSERVED: Process cleanup found no remaining tracked process. INFER: Review is complete; next expected checkpoint is the final scanned evidence push and remote SHA verification.
