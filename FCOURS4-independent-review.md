# FCOURS4 independent review

Reviewed checkout: `FCOURS4-coursier-20261020`, head `59b7363cafa357bea8a6253c2922fcce014066e6`, against `7ab7bfffb49123f9f73c45791e41b6c1ae056e7a`.

## Plan

- [x] Read project guidance, the required engineering and testing documents, the invariant table, and the earlier RCOURS2 review.
- [x] Audit tests against A1–A7 and each of the six named coverage groups.
- [x] Inspect the final response handling, publication path, stream ownership, and cleanup.
- [x] Record each supported issue and the checks that passed.

## Reading and limits

Read the product `README.md` and `DEVELOP.md` in full; there is no root `AGENTS.md`. Read `PHILOSOPHY.md` and `architecture/TESTING.md` in full from the supplied local copies, along with `FCOURS4-findings.md` and `origin/wip/RCOURS2-evidence-20261023:RCOURS2-findings.md`. The main rules used here were public-API end-to-end tests, fail-first evidence, loopback servers and isolated resources, exact error assertions, and checking adjacent boundary cases. I also inspected the final diff, `CacheUrl.scala`, `Downloader.scala`, `DownloadResponseFramingTests.scala`, `DownloadLengthIntegrityTests.scala`, and the `DownloadBufferCapacityTests.scala` edit.

No build or test was run in this review, as instructed. The test-coverage results below are source-level checks. The campaign results in the main findings file were not independently rerun here.

## Regression-test coverage

All A1–A7 cases have corresponding assertions in `DownloadResponseFramingTests.scala`:

| Case | Test evidence | Review |
|---|---|---|
| A1, mixed-case range unit | Lines 391–395 use a saved `abc` prefix and `Content-Range: ByTeS 3-5/6`; the public cache call must produce `abcdef`. | PASS. `CacheUrl.scala:250` now matches the unit case-insensitively. |
| A2, chunked range missing its terminator | Lines 542–555 give a valid 206 range body without the zero chunk and require the exact framing error and no cache/temp file. Lines 572–592 also hold the closing chunk and assert the complete bytes remain temporary until it arrives. | PASS. `Downloader.scala:355–356` drains framing after the range read. |
| A3, extra chunked range data | Lines 557–570 append `XYZ` beyond the declared range and require the exact overlong error, with no publication. | PASS. The raw response counter rejects the overrun at `Downloader.scala:907–913`. |
| A4, no-body statuses and HEAD | Lines 397–400 cover unsolicited 304; lines 594–603 cover 204, 205, and 304 with a saved prefix. Lines 513–539 verify HEAD metadata and an empty response stream using a real local `HttpServer`. | PASS for the requested boundaries. The HEAD case exercises `ConnectionBuilder` directly, since `doDownload` always issues GET. |
| A5, stream failure cleanup | Lines 402–420 cover truncated chunked and gzip bodies. Lines 648–668 add invalid gzip trailer/header cases. `downloadErrorIs` at 341–346 checks the full message and absence of final and temporary files. | PASS for the tested framing and decoder failures. |
| A6, encoded wire-length integrity | Lines 422–434 cover a short deflate response; lines 690–708 check gzip and deflate against their declared wire lengths. | PASS. The cases compare response bytes, not decoded artifact length. |
| A7, coding-name case and decoding | Lines 436–447 cover mixed-case gzip, lines 615–627 mixed-case deflate, and lines 729–737 reverse decoding of stacked mixed-case codings. | PASS. |

The six broader groups are also represented: (1) range-unit case variants (391–395); (2) chunked range completion and excess data (542–592, 557–570); (3) no-body responses and HEAD metadata (397–400, 513–539, 594–603); (4) deflate, mixed-case gzip, and other encoding rejection (422–447, 605–627); (5) cleanup after chunked and decoder failures (402–420, 648–668); and (6) concurrent same-file callers on both failure and success paths (495–511). In the last group, both callers complete on the short-body error, and a successful shared download asserts that only one GET was sent.

OBSERVED: The new scenarios use `FileCache.file(Artifact(url))` with a real loopback server bound to port 0 and a per-scenario temporary cache directory (`DownloadResponseFramingTests.scala:182–250, 258`). Server sockets, clients, and executor services are released in `finally` (`294–321`). Error checks compare complete text (`341–368`). The separate HEAD probe also closes its connection and server (`513–539`). The changed buffer test replaces the prior unsupported `custom` encoding scenario with a real deflate stream; no other existing test assertion is changed in the diff.

## Implementation findings

### F1 — The status check rejects valid body-bearing 203 responses

**OBSERVED:** `Downloader.scala:254–258` throws for every HTTP status except 200 and 206. In the compared baseline, 404, 403, and 401 were handled specially; other statuses continued to the body read. The new README says statuses without an artifact body are rejected (`README.md:28–31`), while this guard also rejects responses that do carry a representation.

**Evidence:** RFC 9110 says a GET 203 response carries a potentially modified or enhanced representation of the target resource and is successful ([RFC 9110, section 6.4.1 and 15.3.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.4)). A local server returning `203 Non-Authoritative Information`, `Content-Length: 6`, and `abcdef` therefore supplies a body-bearing response that the old path read and this path rejects with `Unexpected HTTP status 203 ... expected 200 or 206`.

**Suggested reproduction and scope:** Add a fail-first loopback test through `FileCache.file` for a 203 response with a complete body, then decide the accepted body-bearing success statuses explicitly. Keep rejection tests for 204, 205, and unsolicited 304. The present guard makes the “no artifact body” rule broader than the stated behavior.

### F2 — Encoded-body error handling labels unrelated I/O failures as encoding errors

**OBSERVED:** The `catch` at `Downloader.scala:360–365` converts any `IOException` in the enclosing `try` (`319–358`) to `Invalid Content-Encoding`. That block includes decoder construction, but also `Files.newOutputStream` (`338–344`), writes/flushes through `readFullyTo` (`346–354`), `response.finish` (`356`), and output close (`358`). Thus a valid gzip response with a temp-file open/write failure, or a non-chunked transport read failure while finishing the response, can be reported as malformed gzip. The underlying exception is retained as a cause, but the user-facing message still points at the wrong source.

**Suggested reproduction and scope:** With valid gzip bytes from a real loopback response, cause the temporary-file open or write to throw `IOException` (for example, prepare the expected temporary path as a directory) and assert the result reports the file I/O failure rather than `Invalid Content-Encoding`. Restrict the encoding label to decoder construction/read failures; preserve the generic file or transport error path for other operations.

## Other implementation checks

- **PASS — framing and counts:** The response wrapper counts de-chunked response bytes before decoding (`Downloader.scala:310–317, 864–928`). Fixed lengths bound reads; early EOF produces expected/received counts; chunked reads surface missing endings and reject overlong range data. The new range tests exercise both edges.
- **PASS — decoding and ownership:** Coding tokens are normalized with `Locale.ROOT` and decoded in reverse order (`269–287, 321–333`). The explicit `Inflater` is ended on close (`326–330`); the outer stream is closed in `finally` (`367`). The connection is closed and an incomplete download's temp file is removed in the outer `finally` (`399–406`).
- **PASS — publication ordering:** The final move occurs after `readFullyTo` and `response.finish` (`345–385`), so incomplete framing and explicit validation errors occur before cache publication.
- **PASS — error text and coverage shape:** New errors include the URL and relevant expected/actual values; the test helper asserts full error strings. I found no reflection, mocks, skipped tests, raised timeouts, reduced iteration limits, or weakened pre-existing assertions in the reviewed diff.

## Review result

Two implementation concerns are supported above: F1 is a response-status compatibility regression, and F2 is a misleading error classification for encoded downloads. The A1–A7 and six requested coverage groups have source-level tests. No product changes, test runs, builds, pushes, merges, or external requests were made in this review.

## Follow-up review — head `dd9cd1e0a0b25419d41abcdd6996b29733d2f949`

### Reading and limits

The first-pass reading list above remains applicable. For this follow-up I inspected the diff from `59b7363cafa357bea8a6253c2922fcce014066e6` to this head, the changed portions of `Downloader.scala` and `DownloadResponseFramingTests.scala`, and the test OS matrix in `.github/workflows/ci.yml`. The checkout was clean at the reviewed head. I did not run tests or builds; the before-fix 33/35 result is reported by the parent and was not independently verified here.

### Prior findings

- **F1 — PASS, addressed in source. OBSERVED:** `Downloader.scala:254–258` now allows 2xx responses other than 204 and 205. The added loopback case at `DownloadResponseFramingTests.scala:797–802` checks complete artifact bytes for 201, 202, 203, and 207. Existing no-body rejection coverage remains for 204/205 and unsolicited 304 (`DownloadResponseFramingTests.scala:397–400, 594–603`). This matches the stated body-bearing-success behavior.
- **F2 — PASS, addressed in source. OBSERVED:** `Downloader.scala:323–337` wraps decoder construction, then wraps only decoder reads in `DecodingInputStream` (`Downloader.scala:930–955`). Raw response reads convert their own I/O failures to response-framing errors (`Downloader.scala:887–897`). File open/write, `response.finish`, and output close at `Downloader.scala:342–364` sit outside the decoding-error wrapper, so those failures retain their generic file/response path. The added output-directory case at `DownloadResponseFramingTests.scala:804–814` checks that a gzip download's file-open error is not labeled as an encoding error.

### New changed-line concern

- **F3 — INFER, test portability risk:** `DownloadResponseFramingTests.scala:812` hard-codes the Linux error class and message `java.nio.file.FileSystemException (...: Is a directory)`. The test matrix includes `windows-latest` at `.github/workflows/ci.yml:72` as well as macOS at lines 68–71. Opening an existing directory for output can map to a different exception class/message on Windows (for example, an access-denied filesystem error), so this exact assertion may fail on that supported test platform even though the product behavior is correct. **Suggested check:** run this focused case on the Windows matrix. If it differs, keep the real filesystem setup and full assertion, but account for the platform-specific exception class/message in the expected error; do not weaken it to a substring-only check. I did not execute it on Windows, so this remains an inference.

### Other changed-line check

**PASS — OBSERVED:** The accepted-status predicate preserves rejection of all non-2xx responses and explicitly excludes 204/205; the new status test covers several other 2xx codes. Decoder-construction failure closes the previous stream through the surrounding `finally` (`Downloader.scala:319–364`), and the explicit inflater still ends on close (`327–332`). The raw read wrapper's framing conversion occurs before decoder reads, so an underlying response I/O error is not relabeled as a coding error. I found no other concrete regression in these changed lines.

## Final follow-up — portable output-error assertion

**Reviewed source:** `verify/FCOURS4-portable-error-before` (`20cf972a1e1510b79e9eb20099d3c3689b2ef551`), reading `modules/cache/jvm/src/test/scala/coursier/cache/DownloadResponseFramingTests.scala` with `git show`. This is a test-only review fixture; its restored broad production catch is intentional setup for the parent's fail-first check and is not treated as final product code. No tests or builds were run.

**F3 — PASS, portability concern resolved. OBSERVED:** The fixture creates the same temporary path as a directory, obtains the host's complete native exception by calling public `utest` `assertThrows[java.io.IOException]` around `Files.newOutputStream(temporaryFile.toPath).close()` (`DownloadResponseFramingTests.scala:255–264`), and records the exception class, full message, and URL. The actual FileCache download must then return exactly that full error string (`813–822`), while `downloadErrorIs` also checks that final and temporary cache entries are absent (`355–360`). This avoids assuming Linux's exception class or wording while keeping a complete assertion; it neither skips platforms nor accepts a message fragment. With the parent-reported fail-first result, the assertion also distinguishes generic output-I/O propagation from the old encoding-error label. The earlier Windows portability concern is therefore resolved at source level.

**Final verdict:** The two implementation findings were addressed in source at head `dd9cd1e0a0b25419d41abcdd6996b29733d2f949`; the output-error regression test now derives portable full-text expectations from the same native filesystem operation. No additional concrete concern was found in the reviewed correction.

## Final source follow-up — head `54ca4daea0fab025e1554991034c464ecd56cfae`

**Reading and limits:** I inspected the commit-to-commit source and test delta from the previously cleared `dd9cd1e0a0b25419d41abcdd6996b29733d2f949`, and read the resulting source with `git show`. The working tree was intentionally set up for a separate test-only fail-first check; I assessed the committed head, not that working-tree variation. The 37/40 pre-fix outcome and exact failing tests are reported by the parent, not independently run here. No builds or tests were run.

**Prior status/error behavior — PASS. OBSERVED:** `Downloader.scala:254–258` now applies the new status rejection only when `code < 400`. The earlier special 401/403/404 outcomes remain before this guard (`247–252`), while 500 reaches the existing `conn.getInputStream` path, which preserves the JDK `IOException` text. The new real-server case asserts the complete original 500 message (`DownloadResponseFramingTests.scala:825–829`). This restores the previous error contract without undoing the accepted body-bearing 2xx responses or 204/205 checks.

**Close-delimited progress — PASS. OBSERVED:** `ResponseInputStream` now records whether content is encoded (`Downloader.scala:310–318, 863–870`). Its `available()` continues to return the end signal when framing is complete, preserves the positive lookahead signal for encoded data, and delegates to the underlying response stream for unencoded data (`919–925`). This avoids claiming that another byte is immediately readable for a plain close-delimited response while retaining encoded-stream lookahead. The new split-body test gates the peer after the first three bytes and checks that those bytes reached the temporary file and public progress before releasing the next fragment (`DownloadResponseFramingTests.scala:831–839`). The previously reported existing location-ownership failure is consistent with loss of the 500 error path; that test was not edited in this diff.

**Final verdict:** Both newly reported regressions are addressed by the reviewed source delta. The 500 path preserves its original error, and `available()` now distinguishes raw from encoded response streams for progress and decoder lookahead. No additional concrete concern was found in these changes. These are source-level conclusions; the parent's post-fix test runs remain the execution evidence.

## Final narrow follow-up — head `ac74fa03ab018e66a67f293dbae7a4fe3a7bd367`

**Reading and limits:** I inspected the commit-to-commit delta from `54ca4daea0fab025e1554991034c464ecd56cfae` to this head and read the final guard and new test from the committed files. The parent-reported 101/105 fail-first result was not independently run here. No builds or tests were run.

**Status predicate and wording — PASS. OBSERVED:** `Downloader.scala:254–258` rejects only 1xx, 204, 205, and 304 in the new guard. Earlier special handling for 401, 403, and 404 remains in place (`247–252`); other error statuses and body-bearing redirects continue through the prior `URLConnection` handling. The error text now says “expected a response with an artifact body,” which describes the predicate without claiming that every accepted response is successful. This preserves the existing no-follow redirect-body behavior while rejecting the no-body statuses identified in the invariant table.

**Redirect case — PASS. OBSERVED:** The new public cache scenario at `DownloadResponseFramingTests.scala:848–853` sends full bodies for 301, 302, 303, 307, and 308 without a `Location` header and requires the downloaded bytes and published file to match. It adds the stated boundary without editing existing redirect assertions. No other concern was found in this narrow delta.

**Verdict:** The previously reported redirect-body regression is addressed at source level. The reviewed predicate, error wording, and real-server cases align; post-fix execution remains the parent's verification step.
