# Existing test edits

Only DownloadBufferCapacityTests.scala changes in this lane. All twelve cases were added by the unmerged checkpoint branch; no previously merged cache assertion is edited.

- Range HEAD count: `headRequests.get() > 0` -> `headRequests.get() == 0`. The public FileCache persisted-prefix path calls GET directly; remoteLastModified is the HEAD path and is not used here. Exact Range, GET count, prefix/suffix bytes, rename, stream closure, logger length and progress assertions remain. With product fix removed, this scenario still fails at the response-size assertion.
- Existing tiny and empty cases retain all original byte, closure and upper-bound assertions and gain an exact read-array-size assertion. Configured-cap case gains the same exact assertion. Range gains an exact suffix-size assertion. These strengthen the checks.
- Unknown-length case retains its checks and adds an exact default-array-size assertion.
- Gzip case title changes from `retains the configured maximum while returning decoded bytes` to `with known encoded length returns all decoded bytes`. Assertions unchanged. The public handler observes GZIPInputStream's compressed-side buffer, so the title now states the behavior the scenario directly proves. Unknown and other encoding controls directly check the default buffer size; the production condition excludes gzip from the smaller size.
- New cases cover 199-byte POM, one byte, identity, other content encoding, unknown-length identity, and one byte below/at/above the default.
- scalafmt 3.9.6 formats the unmerged test file without changing its checks or waits.

The final test source is run ten times against unchanged Downloader after these edits, then thirty times against the fix, with one attempt per case per run. No iteration, timeout, retry or assertion is loosened.
