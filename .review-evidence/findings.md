# revcoursier2 independent review

Target: https://github.com/CodexCoder21Organization/coursier/pull/5 at 5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3.
Started approximately 2026-10-17 02:55 UTC; deadline approximately 03:30 UTC.

## Plan
- DONE: Read required Testing Architecture and Philosophy sections, predecessor review and round-2 findings; locate repository with github-repos skill and fresh clone.
- IN PROGRESS: Read repository instructions/README; inspect destination ownership, canonical path behavior, shared lock namespace, and fixtures.
- TODO: Run unchanged round-2 Mill command on exact head under a local build slot; audit native gate artifacts and first attempts.
- TODO: Review refutation, record any findings with location/observations/inference/fix; push only evidence and finish process audit.

## Required reading
OBSERVED: Read Testing Architecture sections Test-Driven Development, Never Calibrate a Reproducer to a Machine, Tests Must Be Hermetically Sealed, Test Verdicts Must Be Hardware-Independent; Philosophy sections Reproduce a Bug Before You Fix It, A Refuted Hypothesis Is a Successful Outcome, Never Hide a Failure. Read complete revcoursier1 and coursierfetch2 findings. Repository README/instructions follow next.

## Initial evidence
OBSERVED: Previous review raised two P2 test issues: constructor defaults read user configuration; the same-location guard signaled before actual contention and its dot path was normalized before coordination. Round 2 claims explicit full constructors and a symlink plus public watching callback address both.
INFER: Equal URLs selected the same old lock. Therefore a same-URL/same-destination guard should pass the URL-based product; independent destinations sharing a URL are the appropriate old-product discriminator. Native evidence and current guard source will be checked.

## FLAKES ENCOUNTERED
None observed yet.

## Challenges for the orchestrator
None newly established.

## 02:58 UTC — source and native gate audit
OBSERVED: Fresh checkout remains exact head after the initial fetch/rebase. Root README read; no root AGENTS.md exists. Four-file diff is 359 additions and one deletion: README states ownership behavior; CacheLocks adds a canonical-file adapter; Downloader changes one coordinator call; the new test suite covers independent success, independent error, and shared output through a symbolic-link alias.
OBSERVED: Independently downloaded all six artifacts in https://github.com/CodexCoder21Organization/coursier/actions/runs/38996343860. Native XML audit confirms ordinary suites 9/9, isolated malformed-config suite 3/3, unchanged FileCacheTests neighbors 6/6: total 18/18 first attempts, zero skipped and no prior-attempt/history elements. Exit codes are zero. Full SHA proofs and all four file hashes match the candidate. The config job has only the declared three-line test-JVM property addition to the build module.
OBSERVED: Product-only reversal has native 2/3 failures, both exact secondEntered.await(30, TimeUnit.SECONDS) assertions with latch count 1; the same-location guard passes. Two product hashes match b051ebea6dcadacf3f781687c1a97e89f4eb69f9; tests and README match the candidate. Exit code is one. Audit saved in .review-evidence/native-audit.json.
INFER: The round-2 refutation is correct. Equal URL strings select the same old coordinator, so a same-URL/same-file guard cannot distinguish the previous product. It still demonstrates the promised observable invariant: distinct lexical aliases, equal canonical destinations, actual same-file waiting while owner remains held, one GET and exact shared bytes. Both independent-location tests distinguish the old product.
OBSERVED: Exact original command ./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests is running under a local build slot with a 20-minute hard deadline and LC_ALL=C.UTF-8. No test rows yet; compilation is not counted as a pass. A checkout-local cs link supplies the executable name required by the unchanged build, targeting /usr/local/bin/coursier.
OBSERVED: Live PR read confirms OPEN and DRAFT at the pinned head. No PR mutation or CI monitoring occurred.

## 02:59 UTC — exact-head local test milestone
OBSERVED: The required unchanged Mill command completed at exact head, exit zero, native XML 3/3 first attempts, zero errors/failures/skipped/history. Durations: independent success 0.131s, independent owner error 0.014s, same-file alias 0.043s. Product, tests and README remain byte-identical to the pinned head. Local build slot released immediately on command exit.
OBSERVED: The guard's public callback can originate from either the JVM coordinator's waiter branch or the existing OS file-lock waiter branch. That is appropriate for a public-behavior guard: it proves one same-file owner and actual waiting, rather than requiring a particular internal coordinator. The two distinct-destination cases remain the discriminators for this change.
INFER: No further positive test batch is needed. Remaining work is source-level path/error/namespace review, documentation of limits, durable evidence checkpoint and final cleanup.

## 03:00 UTC — fixture settings follow-through experiment
OBSERVED: FileCache.download constructs Downloader with positional arguments only through clock. It passes FileCache.retry as the deprecated sslRetry argument, then omits retryCount, retryBackoffInitialDelay and retryBackoffMultiplier. Downloader therefore evaluates CacheDefaults for those last three arguments even though the fixture supplies explicit FileCache constructor values. CacheDefaults reads the corresponding coursier.exception-retry system properties.
The mechanism being checked is that the new same-file waiter can still inherit unrelated process retry configuration through Downloader defaults, and the evidence sought is a native failure at the unchanged fixture's 30-second future wait when only the test JVM receives the supported initial-delay property, while its explicit FileCache initial-delay value remains 10 milliseconds.
INFER: The watching callback occurs before checkDownload returns None. Retry.retryOpt then sleeps using Downloader's default delay, so setting that supported property to one minute should prevent the second future from finishing inside the unchanged guard bound. This would establish a remaining fixture-input gap, rather than a regression in the destination coordinator. No product change is proposed; first run will keep the complete existing test source unchanged.

## Ownership and path review table
| Surface | OBSERVED | INFER / review result |
| --- | --- | --- |
| Distinct stable output locations | Different canonical strings; both native independent cases finish before held owner release | Correctly independent, including first-owner HTTP error |
| Same stable output through aliases | Real link gives different absolute spellings and equal canonical strings; waiting callback before release, exact byte 1, one GET | Preserved same-file exclusion is proven through the public API |
| Lock entry lifetime | Existing putIfAbsent, Some/None and try/finally removal are unchanged | Exceptions release ownership; no new retained entry |
| Missing parent directories | Owned Java experiment resolves alias and real path to equal canonical names before parent creation; ordinary directory/file creation leaves name stable | Ordinary initial downloads work without requiring pre-existing artifact directories |
| Relative and absolute paths | Owned Java experiment returns equal canonical strings | Canonicalization does more than lexical normalization |
| Invalid link cycle | Owned Java experiment gets IOException for canonicalization and FileSystemException for actual write | No demonstrated valid download lost; Downloader maps canonical I/O failures through its existing error path |
| Link target changed during operation | Filesystem path can change identity after lookup; publication and OS lock paths are also lexical and unchanged by this diff | Canonical strings cannot freeze a mutable namespace. No demonstrated regression under stable cache roots; no promise of safe external retargeting during a download is established |
| Case-insensitive filesystems | Native gates and local run are Linux only; string canonicalization behavior depends on platform | No macOS/Windows runtime claim. Existing OS file lock remains a second ownership layer if canonical spellings differ; a string-key difference alone does not prove duplicate writers |
| URL/path map namespace | All repository callers enumerated: only new private[cache] adapter calls public withUrlLock. Accepted artifact URLs require protocol plus colon and slash; canonical local paths are absolute platform paths | No valid supported URL/path collision identified. Passing a raw path to the public method named withUrlLock would reuse that string, but no such repository caller exists |
| Fixture resources | Owned loopback 127.0.0.1:0 server, owned temporary tree, explicit pool and credentials, latches held until observable events, cleanup releases owner/stops server/pools | No sleep used as test synchronization; constructor credentials issue is fixed. Downloader defaults are under the separate controlled experiment |

## Review finding — P2: Explicit fixture retry settings do not reach the waiter
Location: https://github.com/CodexCoder21Organization/coursier/blob/5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3/modules/cache/jvm/src/test/scala/coursier/cache/FileCacheLocationOwnershipTests.scala#L303 and https://github.com/CodexCoder21Organization/coursier/blob/5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3/modules/cache/jvm/src/test/scala/coursier/cache/FileCacheLocationOwnershipTests.scala#L330 . Owning call path: https://github.com/CodexCoder21Organization/coursier/blob/5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3/modules/cache/jvm/src/main/scala/coursier/cache/FileCache.scala#L115 .
OBSERVED: All six fixture constructors explicitly supply retry=5, retryBackoffInitialDelay=10.milliseconds and retryBackoffMultiplier=2.0. FileCache.download passes retry into Downloader's deprecated sslRetry slot, then stops its positional call at clock. Downloader's active retryCount/backoff arguments are omitted and evaluate CacheDefaults, which reads unrelated process settings. The old and new product both have that forwarding gap; the newly introduced ownership suite inherits it.
OBSERVED: Ordinary exact-head local run has native 3/3. Keeping every product/test/README byte unchanged and adding only `-Dcoursier.exception-retry-backoff-initial-delay=1m` to the forked test JVM produces native 2/3, exit one, zero skipped/history: both independent-location cases pass; `same location joins its owner` fails at line 330 with java.util.concurrent.TimeoutException, full message `Future timed out after [30 seconds]`. Its native duration is approximately 30 seconds. The public watching callback has occurred and owner is released, but Retry.retryOpt takes its inherited one-minute wait before checking the published file again.
INFER: The new fixture remains dependent on supported process configuration despite its complete constructors. The malformed-credentials input is fixed, but supplying explicit values at FileCache construction alone does not establish hermetic retry behavior. This is a test-isolation issue exposed by a pre-existing FileCache-to-Downloader forwarding defect, not an observed error in the destination-based lock.
Suggested fix: Forward FileCache.retry to Downloader.retryCount and forward both FileCache backoff values to Downloader's matching active arguments; retain the explicit fixture settings and add a regression check that supported surrounding retry properties cannot replace those explicit values. Do not set global properties inside the fixture or change its 30-second bound. The review brief permits no product edit, so this is returned for the owning implementation lane.
Reproduction: same original Mill command, exact reviewed source, only forkArgs property added through the evidence-only build module patch. Complete native XML, full trace, command log and exact temporary patch are retained under .review-evidence/ambient* on wip/revcoursier2-evidence. The patch has been restored; the checkout has no tracked source/build changes. No repeat run past the failure was submitted.

## FLAKES ENCOUNTERED — new fixture process-setting dependence
OBSERVED: First controlled attempt has one native failure: coursier.cache.FileCacheLocationOwnershipTests / same location joins its owner; java.util.concurrent.TimeoutException: Future timed out after [30 seconds]; FileCacheLocationOwnershipTests.scala:330. This is a deterministic fixture-input defect with the supported one-minute retry setting, not an intermittent sample. Local run has no Actions/buildtest URL. Native XML and complete trace are retained as ambient/test-report.xml; source remains the pinned head. No assertion or bound changed, and no product fix is allowed in this review lane.

## Cross-platform limit
OBSERVED: The stronger alias uses Files.createSymbolicLink. The JDK documents that this may require platform-specific process privileges; this repository's older UtilTests link scenario explicitly avoids Windows, while jvmTests includes the cache suite on Windows. Only Linux native evidence is available here.INFER: A portable relative-versus-absolute alias would avoid that extra platform prerequisite, but no Windows failure was observed in this lane. This is recorded as a verification limit, not a second counted finding. Case-insensitive canonical behavior likewise remains unverified; no product error is claimed from an untested platform.
