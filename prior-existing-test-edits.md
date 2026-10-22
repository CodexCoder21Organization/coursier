# Existing test edits

Only DownloadBufferCapacityTests.scala changes in this lane. All thirteen cases were added by the unmerged checkpoint branch; no previously merged cache assertion is edited.

- Range HEAD count: `headRequests.get() > 0` -> `headRequests.get() == 0`. The public FileCache persisted-prefix path calls GET directly; remoteLastModified is the HEAD path and is not used here. Exact Range, GET count, prefix/suffix bytes, rename, stream closure, logger length and progress assertions remain. With product fix removed, this scenario still fails at the response-size assertion.
- Existing tiny and empty cases retain all original byte, closure and upper-bound assertions and gain an exact read-array-size assertion. Configured-cap case gains the same exact assertion. Range gains an exact suffix-size assertion. These strengthen the checks.
- Unknown-length case retains its checks and adds an exact default-array-size assertion.
- Gzip case title changes from `retains the configured maximum while returning decoded bytes` to `with known encoded length returns all decoded bytes`. Assertions unchanged. The public handler observes GZIPInputStream's compressed-side buffer, so the title now states the behavior the scenario directly proves. Unknown and other encoding controls directly check the default buffer size; the production condition excludes gzip from the smaller size.
- New cases cover 199-byte POM, one byte, lowercase and mixed-case identity, other content encoding, unknown-length identity, and one byte below/at/above the default.
- scalafmt 3.9.6 formats the unmerged test file without changing its checks or waits.

The final thirteen-case test source is now run ten times against unchanged main Downloader: all eight size cases fail10/10 on buffer assertions and all five controls pass10/10. All thirteen final cases previously passed30/30 with one attempt per case per run; after the final backout check, source restoration passes13/13 once and all five compiled download classes again match the measured snapshot. No iteration, timeout, retry or assertion is loosened.

Independent code review additionally found mixed-case identity was missed. A new mixed-case case fails10/10 against the first fix; the corrected case-insensitive comparison passes all thirteen cases30/30. No prior assertion changes for this correction.

Separate evidence-only JoinedWorkload diagnostic copies add event/phase output and public Future outcome reads; they do not edit the fifteen original gate sources or their assertions/timeouts. One new diagnostic explicitly holds the public readiness callback until the existing accept Future expires to force the late-connection ordering. This is separate from both the original450-case gate and the buffer tests.
