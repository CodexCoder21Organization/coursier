# Download length and range test review

## Review scope and material read

The brief identified `cf396abac078f6f5468f087a147cf147a0cd783c` as the starting commit, rebased on main `f7ac8c67`. My first checkout check found that commit on `fix/FCOURS2-download-length-integrity`. Before I finished inspecting the source, the shared checkout advanced to `8bde0ca15c8a441e5fc40536625e6024d74bf61c` (`Reject invalid download buffer sizes before creating cache files`), now HEAD and clean, one commit ahead of the remote branch. That commit adds the positive-buffer guard in `internal/Downloader.scala`; the test files reviewed were unchanged by it. Findings below describe the test coverage in the source tree at `8bde0ca15c8a441e5fc40536625e6024d74bf61c`.

Reviewed `modules/cache/jvm/src/test/scala/coursier/cache/DownloadLengthIntegrityTests.scala`, the existing `DownloadBufferCapacityTests.scala`, `CacheUrl.scala`, and `internal/Downloader.scala`.

Read the project `README.md` and `DEVELOP.md`, plus the complete supplied copies of `DocumentationRepository/architecture/TESTING.md` and `DocumentationRepository/PHILOSOPHY.md` under `FCOURS3-reading/`. The testing guidance emphasizes sealed end-to-end tests through the public API, real dependencies, full error-text assertions, boundary cases, and tests that prove the failure mode rather than only a happy path. No documentation/brief contradiction was found.

No builds or tests were run, per the review brief. The earlier fail/pass counts in the brief were not independently rerun.

## Coverage that is present

- The new suite drives `FileCache.file` against a real loopback HTTP server on an OS-assigned port, with an isolated temporary cache and explicit retry count zero. The server records requests; the test helper returns a valid fallback response on any unexpected extra request, so an accidental restart also changes the outcome. It closes the server, sockets, worker pools, streams, and temporary directory in `finally`.
- Fixed-length success and truncation are covered. Truncated cases assert the full error message with URL, expected length, and received length; the shared error assertion also verifies there is no final artifact or temporary file and that the logger records failure. A paused response separately confirms partial bytes remain in the temporary file while the body is incomplete and disappear after EOF. Tests at `DownloadLengthIntegrityTests.scala:347-380` cover the current short-body cases.
- Exact and zero-length responses, fragmented fixed-length responses with trailing bytes, and a small configured buffer check published bytes and progress do not exceed the declared length (`:382-425`). `DownloadBufferCapacityTests.scala:222-345` additionally records `InputStream.read` request sizes for known, unknown, encoded, and configured-buffer cases.
- Range coverage is broad: valid suffixes with and without `Content-Length`, wrong or missing starts, missing/malformed `Content-Range`, end/total errors, unknown totals, overlong ranges, numeric overflow, a `Content-Length` conflict, unsolicited 206 responses, byte-zero 206, and a server that ignores Range with a full 200 response. Failure cases assert one request and cleanup (`DownloadLengthIntegrityTests.scala:584-776`).
- Close-delimited responses, chunked framing with a conflicting `Content-Length`, redirects, identity/gzip handling, and malformed or very large content lengths have dedicated scenarios. The existing buffer suite covers unknown chunked and identity responses.

## Findings

1. **Truncated fixed-length responses do not exercise received-byte counting across multiple reads.** The short fixed-length tests send six bytes against a declared length of nine with the default buffer (`DownloadLengthIntegrityTests.scala:347-361, 363-380`). Six bytes fit in one read buffer. The short range tests likewise receive only two suffix bytes (`:757-776`). These tests prove the error shape and cleanup, but a regression that reports only the last successful read's count instead of the cumulative count could still pass.

   Add a loopback case using the existing scenario helper with `fixed("9", body)` and `cap = 2`. The six-byte body then requires multiple successful reads before EOF. Assert the exact `expected 9 bytes, received 6 bytes` error, final progress of six, one request, no final artifact, and removal of the temporary file. This stays on the public `FileCache` path and would distinguish cumulative counting from a one-read implementation.

2. **The empty-body buffer test does not assert the zero-length read request.** `DownloadBufferCapacityTests.scala:297-302` requires a read observation with a one-byte backing array, while `assertScenario` only checks that requested lengths are at most the configured maximum (`:194-211`). It never checks `requestedLength == 0`. The new integrity test checks that a declared-zero response publishes no bytes (`DownloadLengthIntegrityTests.scala:389-395`), but the HTTP framing means it cannot distinguish a zero-byte read from a one-byte read that immediately reaches EOF. The implementation intentionally makes a zero-byte read for this boundary (`Downloader.scala:822-824`), so the test should protect that behavior explicitly. Assert that the recorded known-empty read has `requestedLength == 0` (and a one-byte array if retaining the buffer-allocation contract). The parent brief already identifies this exact test addition as planned.

## Plan status

- [x] Confirm checkout and read project overview/development instructions.
- [x] Read the linked testing and engineering guidance.
- [x] Trace requested behavior to tests and implementation.
- [x] Record findings with locations and proposed tests.
- [x] Send the completed review to the parent agent.
