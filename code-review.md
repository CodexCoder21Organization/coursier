# Independent review: response download buffer sizing

## Scope

Reviewed branch `fix/I419i-response-download-buffers` at `3706f14db08eed0e85ac2907f64a9f10a9298abb` in `/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-coursier-20261020`. The worktree was clean. I read the full README, the complete change to `Downloader.scala`, the new `DownloadBufferCapacityTests.scala`, and the prior full copies of `PHILOSOPHY.md` and `architecture/TESTING.md` at `/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out/I419i-evidence/PHILOSOPHY-read.md` and `TESTING-read.md`.

No build or tests were run in this review. The parent supplied run evidence: the final 12 cases passed 30/30, the seven size cases failed 10/10 with the fix removed while five controls passed 10/10, and the cache module passed 104/104 once. I treated that as supplied evidence, not as a run I independently verified.

## Finding

- **P3 — Compare the `identity` coding token case-insensitively.** `Downloader.scala:274` only recognizes `contentEncoding == "identity"`. HTTP content-coding tokens are case-insensitive ([RFC 9110 §8.4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.4.1)); for example, a response with `Content-Encoding: IDENTITY` is classified as an unknown/other encoding and keeps the full configured buffer, even though the body is unencoded and its content length bounds the bytes read. This does not corrupt output; it leaves the new memory bound unfulfilled for this spelling. The test at `DownloadBufferCapacityTests.scala:256-261` covers only lowercase `identity`. Use a case-insensitive token comparison and cover an uppercase or mixed-case value. RFC 9110 says `identity` is reserved for `Accept-Encoding` and SHOULD NOT be sent in `Content-Encoding` (§8.4), so this is a low-priority compatibility/contract gap rather than a payload correctness issue; the implementation and test already choose to support the lowercase form.

## Correctness checks

- The known-body calculation uses Long arithmetic, clamps to the positive configured `Int` buffer size before converting back to `Int`, and takes a one-byte minimum for a known empty body. I found no overflow path in that expression (`Downloader.scala:271-276`).
- The same computed size is used for `BufferedInputStream` and `readFullyTo` (`:278-301`), so the downloader's two byte-array allocations share the response bound.
- On a partial response, `getContentLengthLong` describes the response body (the remaining Range bytes); the persisted prefix is only added for progress/logging and is passed separately to `readFullyTo` (`:253-255, 271-301`). The new Range case asserts that a three-byte suffix uses a three-byte buffer, sends `Range: bytes=3-`, and produces the six-byte final artifact (`DownloadBufferCapacityTests.scala:341-551`).
- The read path retains both stream and output cleanup in `finally`, and the test harness asserts stream counts and deletes the per-scenario directory (`Downloader.scala:278-304`; `DownloadBufferCapacityTests.scala:194-211, 174-192, 533-569`). I found no new resource-lifetime regression.
- The gzip case deliberately retains the configured buffer and checks decoded output. Its observer wraps the network stream below `GZIPInputStream`, so it does not measure the outer `BufferedInputStream` buffer; the case proves payload behavior but does not independently assert the gzip buffer size. The source condition itself leaves encoded bodies at `bufferSize` (`Downloader.scala:273-282`).

## Review checklist

- Read project and engineering/testing instructions: done.
- Review source arithmetic, encoding handling, empty bodies, Range resumes and cleanup: done.
- Inspect tests and README claims against the source: done.
- Run builds/tests: not run, per review-only assignment.
