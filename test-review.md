# Independent test review: response buffer fix

## Plan

- [x] Read the full required Engineering Philosophy document.
- [x] Read the full required Testing Architecture document and its sections relevant to this review.
- [x] Read the project README and check for a repository-root `AGENTS.md` (none was present).
- [x] Inspect the changed tests and downloader path without building or running tests.
- [x] Record concrete test gaps and the observed implementation state.

## Documents read

- `/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out/I419i-evidence/PHILOSOPHY-read.md` — all 93 lines. Relevant guidance: reproduce bugs before fixing them, test end-to-end against real dependencies, and cover edge and boundary cases.
- `/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out/I419i-evidence/TESTING-read.md` — all 1,052 lines. Relevant guidance: hermetic tests, TDD, public-API behavior, real dependencies, exhaustive boundary combinations, and checking each asserted contract rather than a nearby symptom.
- Repository `README.md` — read in full. No root `AGENTS.md` exists in the checkout.

## Observed coverage and implementation

The added tests drive `FileCache.file` against an ephemeral loopback `HttpServer`, record byte-array read sizes from the response stream, verify the downloaded bytes, and close the stream and executors. They cover short and empty known bodies, a 199-byte POM, one byte, identity encoding, another encoding, the configured-size boundary, an unknown chunked response, gzip decoding, and a Range resume where the server returns only the three-byte suffix for a six-byte artifact.

The current uncommitted change in `Downloader.scala` reads `conn.getContentLengthLong`, shrinks the size only for known responses with no encoding or `identity`, and passes that result to both `BufferedInputStream` and `readFullyTo` (lines 271–300). In the Range test, the GET response advertises the suffix length while the logger reports total length including the persisted prefix. This matches the stated algorithm by inspection. No code defect was identified in this pass.

## Findings

1. **Unknown length with an explicit identity encoding is not covered.** The suite checks an unknown chunked response without `Content-Encoding` (test lines 289–294), and a known response with `identity` (lines 248–252), but not their combination. The contract says an unknown length keeps the configured size regardless of encoding. An error in the condition that treats `identity` as sufficient even when the length is unknown could therefore pass the current cases and select a one-byte buffer for a chunked identity response. Add an unknown-length response with `Content-Encoding: identity` and assert it retains `CacheDefaults.bufferSize`.

2. **Several size assertions prove only an upper bound, not the named/exact size.** `assertScenario` checks `requestedLength <= maximumReadSize` and `arrayLength <= maximumReadSize` (lines 203–205). The 199-byte POM and three-byte identity cases pass even if the implementation chooses a smaller buffer than the required `min(configuredSize, responseLength)`; the configured-maximum case (lines 276–287) likewise permits any size from 1 through 8192. The near-default boundary cases and the unknown/custom-encoding cases do assert that a read array of the expected size occurs, so this gap is limited to the other cases. If exact sizing is part of the contract, assert the expected size in the uncompressed cases and the custom-maximum case. For gzip, the probe is below `GZIPInputStream`, whose own read buffer obscures the outer transfer buffer; the current gzip test verifies decoded bytes and an upper bound but cannot prove that the configured size is retained. Either observe the transfer chunking through a suitable public signal (for example, the existing progress logger) or narrow the test's claim to boundedness.

## Limits of this review

No build or test was run, as requested. The current implementation and findings are based on source inspection only.
