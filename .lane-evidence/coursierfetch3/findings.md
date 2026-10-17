# coursierfetch3 findings
Started 2026-10-17 03:04 UTC; deadline 03:34 UTC.

## Plan
- DONE: Read prior review/implementation findings, required Testing/Philosophy sections, repository README and source; fresh clone and initial fetch/rebase.
- DONE: Verify exact-head retry-property failure with native XML; evidence checkpoint follows.
- REFUTED: A test-only constructor change can close this gap. The requested explicit arguments are already present; production FileCache.download must forward them.
- PENDING: Push milestone evidence, check PR draft/state, finish process audit.

## Required reading
OBSERVED: Read revcoursier2 and coursierfetch2 findings; Testing Architecture sections Test-Driven Development, Never Calibrate a Reproducer to a Machine, Tests Must Be Hermetically Sealed, Test Verdicts Must Be Hardware-Independent; Philosophy sections Reproduce a Bug Before You Fix It, A Refuted Hypothesis Is a Successful Outcome, Never Hide a Failure. Repository README read; root AGENTS.md absent. Applied github-repos skill to locate coursier.

## Mechanism and pre-edit evidence
The mechanism is that FileCache.download passes its retry value to Downloader.sslRetry and omits Downloader.retryCount, retryBackoffInitialDelay and retryBackoffMultiplier; the evidence that demonstrates it is the explicit positional call at FileCache.scala:119-140 and Downloader parameters at Downloader.scala:47-53, plus the review native 2/3 retry-property result, to be independently repeated here.
OBSERVED: All six fixture FileCache constructors at exact head 5470aee6 already explicitly supply retry=5, retryBackoffInitialDelay=10.milliseconds and retryBackoffMultiplier=2.0. FileCache.download is private, so fixture construction has no public means of forwarding those settings to Downloader.
INFER: The requested test-only constructor fix is already present and cannot close the active Downloader input gap. Product forwarding appears necessary; no product edit is authorized by this brief. The destination-coordination production fix remains untouched.

## Invariants
| Resource | Owner / observation | Check |
| --- | --- | --- |
| Fixture retry policy | Explicit FileCache settings must reach download operations | Same suite with test-JVM one-minute supported retry property |
| Destination coordination | Canonical destination selects ownership | Product files must remain byte-identical to reviewed head |
| Test resources | Owned loopback server, temporary path and executors | Existing cleanup/assertions retained unchanged |

## Existing-test edits
None.

## FLAKES ENCOUNTERED
Pending exact-head repeat of the assigned deterministic retry-setting failure.

## Challenges for the orchestrator
OBSERVED: The suggested test-only fix contradicts source: every fixture already supplies all three FileCache retry arguments. The review itself recommends forwarding settings in production FileCache.download. A test-only replacement of identical constructor inputs cannot affect the omitted Downloader arguments.

## 03:10 UTC — native negative control and scope result
OBSERVED: Exact reviewed head 5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3, retained original Mill command and test-JVM-only `-Dcoursier.exception-retry-backoff-initial-delay=1m` produce native 2/3 first attempts, exit one, zero skipped and zero earlier-attempt/history elements. Independent success passes in 0.171s; independent error passes in 0.031s; same location joins its owner fails in 30.041s with java.util.concurrent.TimeoutException and full message `Future timed out after [30 seconds]`, at FileCacheLocationOwnershipTests.scala:330. Complete native trace and command log are in the evidence checkpoint. This repeats the exact assigned review failure with no test or product modification.
OBSERVED: Source audit counts six FileCache constructors, six explicit retry=5 arguments, six explicit 10.milliseconds delay arguments and six explicit 2.0 multiplier arguments. These are the complete available FileCache constructor fields; the test-only requested edit is already present at the assigned baseline. Product FileCache.scala, CacheLocks.scala, internal/Downloader.scala and the complete ownership suite remain byte-identical to the pinned head. The invocation-only build-module patch is restored after the run.
INFER: A fixture-only constructor edit cannot satisfy the green control. FileCache.download constructs Downloader without its final three active retry arguments. The necessary product correction is to pass FileCache.retry into Downloader.retryCount and pass FileCache.retryBackoffInitialDelay and FileCache.retryBackoffMultiplier to the corresponding Downloader arguments. The destination ownership implementation can remain unchanged. No product forwarding edit is authorized by the test-only brief, and none has been made. No setter, subclass of the fixture, or repeated identical constructor argument can change the private FileCache.download call.
OBSERVED: No existing test, assertion, iteration count, timeout or README changed. No positive or mutation gate is claimed for a correction that has not been made. Repeating the original property control beyond this failure would add no evidence and has not been done.

## FLAKES ENCOUNTERED — assigned retry-setting failure
OBSERVED: First attempt, same location joins its owner, exact native error `java.util.concurrent.TimeoutException: Future timed out after [30 seconds]`; public FileCache.file path at the same-file result wait. Local run, so there is no Actions/buildtest run URL. Complete native XML and trace are retained separately from the PR. The mechanism and minimal required product correction are recorded above; this is the assigned deterministic configuration defect.

## Brief audit
- DONE: Read both named findings and review reproduction evidence; initial fresh clone, README, fetch/rebase.
- DONE: Retry-property red control reproduced at exactly 5470aee6, native 2/3 first attempts.
- REFUTED: Constructing every fixture with explicit retry count/backoff settings is a new available fix; all six already have those exact settings. The defect belongs to product argument forwarding.
- DONE: Canonical-destination product implementation untouched; state precisely why a separate product change is needed.
- NOT MET: Green retry-property control, 3/3 gate and fix-only mutation control; no authorized test-only correction exists.
- NOT APPLICABLE: Push a corrected source to PR/update its description; no correction has been made and no green result is claimed. Existing draft PR remains unchanged. Evidence is pushed separately.
- IN PROGRESS: Durable evidence push, final PR/process audit.
