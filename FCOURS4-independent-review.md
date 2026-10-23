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
