# FCOURS2 download framing tests — progress

## Plan

- [x] Read repository context and the required testing architecture.
- [x] Read the prior raw-response and buffer-capacity suites; map their public `FileCache[Task]` usage and loopback protocol patterns.
- [x] Add only `modules/cache/jvm/src/test/scala/coursier/cache/DownloadLengthIntegrityTests.scala` with real loopback scenarios and complete result/progress/file/request observations.
- [x] Review the new source against the requested scenario list and cleanup requirements; do not run tests/builds or alter other files.

## Read

- `README.md` and `DEVELOP.md` in `FCOURS2-coursier-20261020`; no project-root `AGENTS.md` was present.
- The complete `/tmp/FCOURS2-TESTING.md` (all 1,052 lines). Relevant rules applied: test public APIs only; reproduce actual malformed/truncated network conditions with real local sockets; no production services, mocks, reflection, retries, or conditional skips; use port 0; assert complete error text; clean up sockets, executors, files in `finally`; retain generous existing wait bounds.
- `/tmp/FCOURS2-RcoursResponseTests.scala` and `modules/cache/jvm/src/test/scala/coursier/cache/DownloadBufferCapacityTests.scala`.

## Established from the task brief (verify-don't-redo)

- Root cause is already identified: response reads are not length-bounded; invalid range responses can silently restart; resumed responses only validate the range start. Existing PR6 commit `f7ac8c67` changed buffers only.
- Product code will return `Left(ArtifactError.DownloadError(message, None))` for the new validation rules. Expected exact message templates are in the parent brief.
- Parent settled resumed-range policy: the open-ended whole-artifact request requires a known positive total and `end == total - 1`; `*` total must fail with the exact Invalid Content-Range message. A valid Content-Range can provide expected response length without Content-Length.

## Test implementation decisions

- Use one fresh `FileCache[Task]` per scenario, `withRetry(0)`, `withChecksums(Seq(None))`, two download workers, and a `ServerSocket` bound to 127.0.0.1:0.
- Record the exact request lines/headers, progress events, result bytes or exact `DownloadError` message, final/temp existence, and temp bytes before cleanup.
- Server future owns all server errors and is joined. The server will allow a response to an unexpected extra request so the test can report request-count failures instead of deadlocking. Teardown closes listener/accepted sockets and joins workers.
- No tests/builds, commits, pushes, product edits, or changes to existing tests are in scope.

## Current checkpoint

- [x] Added the new source file at `modules/cache/jvm/src/test/scala/coursier/cache/DownloadLengthIntegrityTests.scala` with a raw loopback ServerSocket harness, response gates for deterministic fragmentation/paused EOF, request/progress/download-result recording, file-state snapshots, and teardown in `finally`.
- [x] Added the three short-body reproducers: expected 9 / received 6; expected 9 / received 0; and a paused 6-byte prefix that proves no final file exists while pending and that the temp file contains the bytes before EOF, then expects the exact incomplete-response error and temp cleanup.
- [x] Add the response framing and content-range cases listed below.
- [x] Static review only; no build/test commands per assignment.

## Remaining scenario list

- Fragmented Content-Length 3 (`ab`, progress 2 gate, then `cdef`) returns exactly `abc` and never reports progress above 3.
- Exact Content-Length 6 succeeds; Content-Length 0 ignores trailing wire bytes; cap 2 remains bounded with a fragmented one-byte response; close-delimited body succeeds.
- Chunked framing overrides conflicting Content-Length and returns all `abcdef` with progress through 6. Gzip known encoded length decodes 32 KiB and progress deltas stay at most 8192. Identity encoding is case-insensitive; short identity fails.
- Invalid Content-Length values `-1`, `abc`, overflow, and empty; oversized valid lengths through `Long.MaxValue` fail with exact received count.
- Redirect body length is ignored in favor of final response; a short final response reports the original artifact URL.
- Valid resumed 206 (`bytes 3-5/6`) succeeds with one Range request. Invalid start, missing/malformed range, end mismatch, total mismatch, unknown (`*`) total, end beyond total, overflowed fields, and Content-Length/range conflict fail with exact messages and no final/temp file. Unsolicited 206 from start 3 fails; valid 206 from start 0 succeeds.
- A 200 response that ignores a requested Range replaces the prefix rather than appending. Short 206 with Content-Length or Content-Range-derived expected length fails and removes temp state.

## Expanded checkpoint

- Added exact/zero/close-delimited/chunked/gzip/identity framing behavior, fragmented over-read checks, malformed and oversized Content-Length values, redirects, and the complete resumed Content-Range matrix including `*` rejection and Content-Range-only length enforcement.
- Every failed-download assertion checks the exact `DownloadError.message`, unsuccessful `downloadedArtifact` event, absence of final and temporary paths, and empty captured temporary contents. Successful downloads check exact bytes, final-file presence, temporary-file removal, and success event.
- The fragmentation gate now sits between separately flushed body chunks. Teardown suppresses only the listener-close exception from `accept` after both the stop signal and `ServerSocket.isClosed`; all read/write/server-future failures propagate. The pending snapshot also asserts the download future is incomplete. Nested `finally` blocks preserve executor and directory cleanup if server joining fails.
- Authoring and static review are complete. Parent baseline feedback is pending; no tests/builds have been run by this worker.

## Final review adjustment

- Gzip progress deltas now include the initial increment from zero and require an observed 8192-byte increment as well as the 8192 maximum.
- Each malformed Content-Length value, each valid oversized Content-Length boundary, and each overflowing Content-Range field has an independent named utest so an earlier failure cannot hide later cases.
- Parent is running the final baseline for the complete source. This worker has not run any test or build command.


## Final authoring report

- The new suite contains 40 individually named utest cases in `modules/cache/jvm/src/test/scala/coursier/cache/DownloadLengthIntegrityTests.scala`.
- Only that new Scala source is changed in the checkout. No existing tests, product sources, or build files were edited.
- This worker did not run tests or builds. The parent is running the full baseline against the original product implementation and owns follow-up changes.
