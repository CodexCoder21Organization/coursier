# Download length and range test review

## Review scope and material read

The brief identified `cf396abac078f6f5468f087a147cf147a0cd783c` as the starting commit, rebased on main `f7ac8c67`. The first review inspected the shared checkout at `8bde0ca15c8a441e5fc40536625e6024d74bf61c`. This final review inspected `7ab7bfffb49123f9f73c45791e41b6c1ae056e7a` (`Cover cumulative short reads and zero-byte empty downloads`) on `fix/FCOURS2-download-length-integrity`. The source tree is two commits ahead of `origin/wip/FCOURS2-download-length-integrity-20261022`. Findings below include the final test additions at `7ab7bfffb`.

Reviewed `modules/cache/jvm/src/test/scala/coursier/cache/DownloadLengthIntegrityTests.scala`, the existing `DownloadBufferCapacityTests.scala`, `CacheUrl.scala`, and `internal/Downloader.scala`.

Read the project `README.md` and `DEVELOP.md`, plus the complete supplied copies of `DocumentationRepository/architecture/TESTING.md` and `DocumentationRepository/PHILOSOPHY.md` under `FCOURS3-reading/`. The testing guidance emphasizes sealed end-to-end tests through the public API, real dependencies, full error-text assertions, boundary cases, and tests that prove the failure mode rather than only a happy path. No documentation/brief contradiction was found.

No builds or tests were run, per the review brief. The earlier fail/pass counts in the brief were not independently rerun.

## Coverage that is present

- The new suite drives `FileCache.file` against a real loopback HTTP server on an OS-assigned port, with an isolated temporary cache and explicit retry count zero. The server records requests; the test helper returns a valid fallback response on any unexpected extra request, so an accidental restart also changes the outcome. It closes the server, sockets, worker pools, streams, and temporary directory in `finally`.
- Fixed-length success and truncation are covered. Truncated cases assert the full error message with URL, expected length, and received length; the shared error assertion also verifies there is no final artifact or temporary file and that the logger records failure. A paused response separately confirms partial bytes remain in the temporary file while the body is incomplete and disappear after EOF. Tests at `DownloadLengthIntegrityTests.scala:347-380` cover the current short-body cases.
- Exact and zero-length responses, fragmented fixed-length responses with trailing bytes, and a small configured buffer check published bytes and progress do not exceed the declared length (`:382-425`). `DownloadBufferCapacityTests.scala:222-345` additionally records `InputStream.read` request sizes for known, unknown, encoded, and configured-buffer cases.
- Range coverage is broad: valid suffixes with and without `Content-Length`, wrong or missing starts, missing/malformed `Content-Range`, end/total errors, unknown totals, overlong ranges, numeric overflow, a `Content-Length` conflict, unsolicited 206 responses, byte-zero 206, and a server that ignores Range with a full 200 response. Failure cases assert one request and cleanup (`DownloadLengthIntegrityTests.scala:584-776`).
- Close-delimited responses, chunked framing with a conflicting `Content-Length`, redirects, identity/gzip handling, and malformed or very large content lengths have dedicated scenarios. The existing buffer suite covers unknown chunked and identity responses.

## Findings from the first review (closed by the final additions below)

1. **Truncated fixed-length responses do not exercise received-byte counting across multiple reads.** The short fixed-length tests send six bytes against a declared length of nine with the default buffer (`DownloadLengthIntegrityTests.scala:347-361, 363-380`). Six bytes fit in one read buffer. The short range tests likewise receive only two suffix bytes (`:757-776`). These tests prove the error shape and cleanup, but a regression that reports only the last successful read's count instead of the cumulative count could still pass.

   Add a loopback case using the existing scenario helper with `fixed("9", body)` and `cap = 2`. The six-byte body then requires multiple successful reads before EOF. Assert the exact `expected 9 bytes, received 6 bytes` error, final progress of six, one request, no final artifact, and removal of the temporary file. This stays on the public `FileCache` path and would distinguish cumulative counting from a one-read implementation.

2. **The empty-body buffer test does not assert the zero-length read request.** `DownloadBufferCapacityTests.scala:297-302` requires a read observation with a one-byte backing array, while `assertScenario` only checks that requested lengths are at most the configured maximum (`:194-211`). It never checks `requestedLength == 0`. The new integrity test checks that a declared-zero response publishes no bytes (`DownloadLengthIntegrityTests.scala:389-395`), but the HTTP framing means it cannot distinguish a zero-byte read from a one-byte read that immediately reaches EOF. The implementation intentionally makes a zero-byte read for this boundary (`Downloader.scala:822-824`), so the test should protect that behavior explicitly. Assert that the recorded known-empty read has `requestedLength == 0` (and a one-byte array if retaining the buffer-allocation contract). The parent brief already identifies this exact test addition as planned.

## Plan status

- [x] Confirm checkout and read project overview/development instructions.
- [x] Read the linked testing and engineering guidance.
- [x] Trace requested behavior to tests and implementation.
- [x] Record findings with locations and proposed tests.
- [x] Review the final added scenarios against their intended contracts and the main-production baseline evidence.
- [x] Send the completed review to the parent agent.

## Final review at `7ab7bfffb49123f9f73c45791e41b6c1ae056e7a`

Both findings above are closed by the final test additions. I found no remaining test-comprehensiveness issue in these two scenarios.

1. `DownloadLengthIntegrityTests.scala:363-374` sends six bytes while advertising nine, with `cap = 2`. It requires at least three progress observations, a final cumulative count of six, and each progress increment to be positive and no greater than two. `downloadErrorIs` checks the full URL/expected/received error and also verifies no published file, no leftover temporary file or bytes, and a failure callback; the scenario asserts one request. The test therefore exercises counting across multiple reads and detects the prior behavior where those bytes were incorrectly accepted.
2. `DownloadBufferCapacityTests.scala:304-309` uses a known empty response and requires the observed request lengths to equal exactly `List(0)`, while the backing array length is exactly `List(1)`. The shared assertion also checks that a read was observed, the advertised length is zero, and stream open/close counts balance. This distinguishes a zero-byte read from a one-byte read that merely encounters EOF.

The supplied main-production baseline evidence (`final-main-before.xml`, `final-main-before-result.json`, and `final-main-before-audit.json`) records one attempt with 57 unique cases: 33 failures and 24 passes. Both added cases failed at the intended assertion. The short-read case observed a successful six-byte result with progress `[2, 4, 6]` instead of the expected incomplete-response error. The empty-response case observed `requestedLength = 1` and failed the exact `List(0)` assertion. I did not rerun tests or builds.

For source comparison, `git diff f7ac8c67 HEAD` shows only the eight-line zero-byte test added to the pre-existing `DownloadBufferCapacityTests.scala`; its previous tests and assertions are unchanged. `DownloadLengthIntegrityTests.scala` is a new test file relative to main. The two reviewed test files are byte-for-byte unchanged between the main-production reproduction commit `ad8276cb9c1454abb936dd401850af28d0b79770` and final source `7ab7bfffb49123f9f73c45791e41b6c1ae056e7a`, confirming the recorded baseline used these same final tests.
