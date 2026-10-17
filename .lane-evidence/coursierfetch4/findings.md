# coursierfetch4 findings
Started 2026-10-17 03:08 UTC; deadline 03:48 UTC (40 minutes).

## Plan
- DONE: Required documentation and both named findings read; fresh clone, repository README, initial fetch/rebase; OPEN/DRAFT head 5470aee6 verified.
- IN PROGRESS: Red property control at 5470aee6 and new public retry-policy regression test, then first failing-test checkpoint.
- PENDING: Forward the three active settings in FileCache.download only; keep canonical destination lock untouched.
- PENDING: Fix-only reversal; exact-source three ordinary and three property runs, malformed config and unchanged FileCacheTests neighbors once; audit native first attempts.
- PENDING: Separate test/code reviews, draft PR description update, durable evidence checkpoint and process cleanup.

## Required reading
OBSERVED: Read Testing Architecture sections Test-Driven Development, Never Calibrate a Reproducer to a Machine, Tests Must Be Hermetically Sealed, Test Verdicts Must Be Hardware-Independent; Engineering Philosophy sections Reproduce a Bug Before You Fix It, A Refuted Hypothesis Is a Successful Outcome, Never Hide a Failure. Read complete coursierfetch3 and revcoursier2 findings and root README. Root AGENTS.md is absent. Applied github-repos skill. Status reporting stays in this findings file per lane instructions; no handoff or challenge service operation is authorized.

## Mechanism before product edit
The mechanism is that FileCache.download sends FileCache.retry into deprecated Downloader.sslRetry and omits active retryCount, retryBackoffInitialDelay and retryBackoffMultiplier; the evidence that demonstrates it is the FileCache.scala call ending at clock and Downloader.scala's trailing defaults, plus two prior native property-control failures at 5470aee6. This lane will repeat the red control before any product edit.
OBSERVED: All six existing constructors explicitly configure retry=5, delay=10.milliseconds and multiplier=2.0. Downloader retries same-file waiting through Retry.retryOpt; its inherited one-minute delay exceeds the fixture's unchanged 30-second result bound.

## Invariants
| Resource / setting | Creator / owner / close | Observable contract and test |
| --- | --- | --- |
| Retry count | Caller supplies FileCache retry; Downloader consumes it | A deterministic recoverable URLConnection failure must honor the instance count rather than process defaults |
| Retry initial delay and multiplier | Caller supplies FileCache settings; Downloader consumes them | Public same-file waiter and explicit-settings regression must ignore surrounding process values |
| Destination coordination | Existing canonical path coordinator owns until download returns | Existing three ownership tests unchanged; same output still one GET, independent outputs progress |
| Test resources | Each case owns server, directory and executors | Release latches and stop server/pools in finally, then remove owned directory; no shared property mutation in test |

## Existing-test edits
None so far; new regression will be a separate suite.

## FLAKES ENCOUNTERED
None newly observed yet; the assigned property-control failure is the current reproducer.

## Challenges for the orchestrator
None newly established.

## 03:12 UTC — red control running
OBSERVED: The original Mill ownership command is running locally under one acquired build slot, with a 15-minute hard deadline. Exact source remains 5470aee6; only the evidence-only build module supplies the forked test JVM's supported initial-delay property of one minute. Source/test files are untouched. A new standalone public FileCache regression has been drafted separately; it holds a real HTTP owner, observes its waiting peer, and checks complete shared bytes using explicit retry=2, delay=1 millisecond, multiplier=1.0. It changes no global property and uses owned resources.
INFER: The active retry count affects platform-specific filesystem retry paths, while same-file waiting deliberately permits more iterations than the count. This regression therefore makes no unsupported claim to distinguish retry counts; it directly distinguishes explicit backoff from process delay via the observable public wait.

## 03:13 UTC — exact-head property control red
OBSERVED: At unchanged 5470aee6 the original ownership suite is native 2/3, exit one, zero skipped/history. Both independent locations pass; same location joins its owner fails with java.util.concurrent.TimeoutException, full message `Future timed out after [30 seconds]`, FileCacheLocationOwnershipTests.scala:330, duration 30.034 seconds. XML and full trace are retained in .lane-tools/baseline for the separate evidence checkpoint. The local slot was released on exit.
OBSERVED: The new separate regression now runs on unchanged product with only test-JVM process retry=0, delay=1m and multiplier=10000000 supplied by the evidence-only build patch. Its public waiter must complete three polling observations before owner release. The fixed workload should take a few polling intervals; the old initial delay forces its unchanged 30-second assertion to fail. The original three-case ownership suite has not been edited.

## Registered single final batch (before results)
Three ordinary runs of ownership plus new retry suite; three one-minute-delay-property runs of both suites; one separate retry-suite multiplier control (test-JVM delay=1ms, multiplier=10000000, count=0); one malformed-config run of both suites; unchanged six FileCacheTests neighbors once; one fix-only FileCache reversal with new regression under supported delay property. Positive expected native rows: ordinary 12 + property 12 + multiplier 1 + config 4 + neighbors 6 = 35. Mutation expected 1/1 assertion failure. No additional batch will run after meeting these gates. All tests run on exact candidate source using Mill/uTest, with native XML and first-attempt audit.

## 03:15 UTC — new public assertion-red milestone
OBSERVED: The separate FileCacheRetrySettingsTests regression fails on unchanged FileCache product, native 0/1, exit one, no skipped/history. Exact assertion: `assert(waiterEntered.await(30, TimeUnit.SECONDS))`; latch remains at count one after 30 seconds. Native duration 30.123 seconds, type utest.AssertionError. The owner remains held until three actual public watching callbacks; process delay prevents reaching the second callback. This is an assertion failure, not a compilation or setup failure.
OBSERVED: Test-only milestone committed as 6dda2793 and pushed under wip/coursierfetch4-evidence before any product edit. Existing ownership tests, CacheLocks.scala and Downloader.scala remain unchanged. Complete native results and invocation patch are being attached to the separate evidence branch.
