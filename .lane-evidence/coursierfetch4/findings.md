# coursierfetch4 findings
Started 2026-10-17 03:08 UTC; deadline 03:48 UTC (40 minutes).

## Plan
- DONE: Required documentation and both named findings read; fresh clone, repository README, initial fetch/rebase; OPEN/DRAFT head 5470aee6 verified.
- DONE: Red property control at 5470aee6, new public regression and failing-test checkpoint.
- DONE: Forward three active settings in FileCache.download; canonical destination lock untouched.
- DONE: Fix-only reversal; exact-source ordinary/property 3/3 repetitions, malformed config, multiplier and unchanged neighbors; native first attempts audited.
- DONE: Separate test/code reviews, draft PR update, durable evidence checkpoints and process cleanup.

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

## 03:15 UTC — scoped candidate and exact-source gate
OBSERVED: Product correction at 690a4a6518b1f8a6cdcc41634c6e702fb7f634dc forwards only the three trailing active Downloader arguments by name. Existing deprecated-slot argument, canonical destination coordination, retry implementation, dependency versions and all existing test files remain unchanged. README adds the public instance-settings precedence contract. New separate test suite has 113 lines; round diff stat is 3 files, 121 additions and one deletion. FileCache.scala: forward three arguments; FileCacheRetrySettingsTests.scala: owned public same-file waiting regression; README.md: explicit settings precedence.
OBSERVED: Failing test and native red evidence are durable at https://github.com/CodexCoder21Organization/coursier/tree/wip/coursierfetch4-evidence/.lane-evidence/coursierfetch4 . Candidate source is durable at https://github.com/CodexCoder21Organization/coursier/commit/690a4a6518b1f8a6cdcc41634c6e702fb7f634dc on wip/coursierfetch4-candidate. Existing PR branch has not yet been pushed.
OBSERVED: Registered final workflow is https://github.com/CodexCoder21Organization/coursier/actions/runs/38998059167 . Ten isolated GitHub jobs check out the full exact candidate SHA, preserve native XML, exit code, source hashes and invocation patch. Raw Actions monitoring uses bounded gh run watch at 120-second cadence; watch-build has no raw Actions-run mode. The watcher is tracked as a tool session and downloads native artifacts after any terminal outcome; 23-minute hard maximum. No PR CI is being monitored.

## Independent test review pass
OBSERVED: New regression calls FileCache.file only and uses a real loopback server on port zero, owned directory and executors. The owner GET holds the real download lock before the peer starts. Three downloadLength(watching=true) callbacks force two backoff steps before owner release; one final completion assertion and exact byte/destination/GET assertions prove the observable result. No property mutation, private API, artificial load, shared fixture, timing adjustment or assertion weakening occurs. Cleanup releases the held owner, stops the server/pools and removes the owned tree. The original ownership suite remains byte-identical. Existing six FileCacheTests rows are the unchanged immediate neighbors.
INFER: A same-file retry-count discriminator is inappropriate because Retry.retryOpt explicitly permits extra None results beyond its count; the regression distinguishes backoff behavior, and the separate multiplier job catches omission of that argument. The count is forwarded to its matching named active argument by source inspection; no runtime claim about platform-specific file-access retries is made.

## Independent code review pass
OBSERVED: Repository production Downloader construction has one call site, FileCache.download. Parameters match exactly by name and type; explicit retry no longer reaches only the deprecated sslRetry slot. Other public settings, return types, cache policy, canonical lock lookup and lock cleanup are unchanged. No new interface method or resource lifecycle is introduced. Default-configured callers retain the same values; explicitly configured callers now use their own values consistently with FileCache's existing filesystem Retry. PR source contains only source, tests and README.

## 03:17 UTC — formatting gate correction before final validation
OBSERVED: Repository CI runs scalafmt --check. The formatter identified layout changes in both the new regression and the ownership suite inherited from earlier rounds. The first Actions run was stopped while jobs were in progress; it is excluded from all gate counts, and its artifacts will be retained. No test failure was used to change assertions or timing. The corrected exact candidate is d5eec8d10bf59305b4263bb96e741e45408c9f85, durable on wip/coursierfetch4-candidate. The final workflow checks out that full SHA.
OBSERVED: scalafmt 3.9.6 matches .scalafmt.conf. Final changed-source formatting check exits zero. Mechanical audit confirms all 31 original ownership assertions, all six FileCache constructor argument lists and all six Await.result calls remain expression-identical after whitespace normalization. No fixture value, wait bound, iteration count or assertion changed. CacheLocks.scala and Downloader.scala remain byte-identical to 5470aee6.

## Existing-test edits (final ledger)
- All three FileCacheLocationOwnershipTests scenarios: before, earlier rounds' unformatted source; after, repository scalafmt layout (alignment and lambda/try layout). Justification: required CI formatting check. All 31 assertion expressions, six full constructor argument lists and six result waits are unchanged. No behavioral edit.
- FileCacheTests neighbors: no edit.
- FileCacheRetrySettingsTests is a new suite, not a modification of an existing test.

## 03:18 UTC — final run and live PR state
OBSERVED: Final exact-source workflow is https://github.com/CodexCoder21Organization/coursier/actions/runs/38998189119 at full candidate d5eec8d10bf59305b4263bb96e741e45408c9f85. Bounded native Actions monitor is running at 120-second cadence. Previous run https://github.com/CodexCoder21Organization/coursier/actions/runs/38998059167 is terminal CANCELLED; all of its results are excluded from positive/mutation gate counts.
OBSERVED: Live PR read at 03:17 UTC confirms https://github.com/CodexCoder21Organization/coursier/pull/5 remains OPEN and DRAFT at unchanged 5470aee6. No readiness, merge, publish, deploy or production operation occurred. No model delegation was launched. Canonical ownership source files remain unchanged by a direct git diff check.
OBSERVED: Final round diff stat is four files, 215 additions and 86 deletions: README.md adds explicit-settings precedence; FileCache.scala forwards three active arguments; FileCacheLocationOwnershipTests.scala receives only formatter layout; FileCacheRetrySettingsTests.scala adds the public waiting regression. All investigation data and workflows remain off the PR branch.

## 03:19 UTC — stopped-run audit
OBSERVED: The stopped Actions monitor exited and downloaded all ten artifact directories. Native completed rows in that obsolete candidate were one-minute-delay control property1 (4 successful rows), multiplier (1 successful row) and neighbors (6 successful rows), all exit zero. Other jobs have no result rows. There are no native assertion/error failures in this stopped run. All 11 completed rows remain excluded from final counts because the final formatted source has a different SHA. No test was re-run past an unexplained first-attempt failure.

## 03:20 UTC — final gate met; no more verification batches
OBSERVED: Exact-source final run https://github.com/CodexCoder21Organization/coursier/actions/runs/38998189119 completed, monitor exited zero, and all ten artifact directories downloaded. Strict native audit passes: ordinary1/2/3 each 4/4 (12); property1/2/3 each 4/4 (12); multiplier 1/1; malformed config 4/4; unchanged neighbors 6/6. Total positive 35/35 FIRST ATTEMPTS, zero skipped and zero prior-attempt/history elements. Every command exits zero for positive jobs. No further test batch will run.
OBSERVED: Property control is native 2/3 at 5470aee6 -> 3/3 in each of three final ownership runs, total 9/9. The new direct public regression is ordinary 3/3 and property 3/3, plus multiplier 1/1 and malformed-config 1/1. Each source SHA and all six file hashes match d5eec8d10bf59305b4263bb96e741e45408c9f85, with only the declared invocation-only build patch where needed.
OBSERVED: Fix-only reversal retains final test, README and canonical coordination bytes and restores only FileCache.scala from 5470aee6. Native regression is 0/1 with one intended utest.AssertionError at `assert(waiterEntered.await(30, TimeUnit.SECONDS))`, exit one; no skipped/history. Thus mutation-control gate is 1/1 intended assertion failure. Full trace is retained in native XML.
OBSERVED: Both independent review passes are complete above. Formatting check passes. Existing-test edit ledger is complete. The initial red control and new assertion-red test were pushed before the product edit. Next steps are PR source/body update, final evidence push and cleanup only; PR CI is owned by the orchestrator.

## FLAKES ENCOUNTERED — final audit
OBSERVED: Only assigned red controls failed: original same-file property case (Future timed out after [30 seconds], local exact head), new public regression waiter-progress assertion on unchanged product (local), and the intended FileCache-only reversal in final Actions run. No unrelated first-attempt defect occurred. Every final positive row passed its first attempt.

## 03:21 UTC — PR updated and temporary workflow removed
OBSERVED: https://github.com/CodexCoder21Organization/coursier/pull/5 is updated to exact tested d5eec8d10bf59305b4263bb96e741e45408c9f85 and remains OPEN and DRAFT. The API update response verifies that state and head. Why-first description preserves the original dependency-fetch motivation and exact handoff URL; a separate paragraph explains the independent forwarding defect. Title now covers both destination coordination and instance retry settings. Native gate numbers and run URL, complete existing-test edit ledger, red controls and fix-only reversal are recorded. Required gh pr view body readback exactly matches the submitted file.
OBSERVED: Remote and local wip/gha-run/coursierfetch4 branches are removed after the workflow terminated. PR source contains no workflow, investigation, log or artifact file. No test or build was run after the final gate. No PR CI monitor was started; the orchestrator owns that wait.
OBSERVED: Final source-level review after formatting confirms the same three argument additions and unchanged canonical coordinator. Formatting changes only layout, with original assertions, constructor lists and waits independently compared. Both local red runs have native first-attempt history count zero and no skipped rows.

## Brief item audit
- DONE: Read both named findings and required documentation; fresh clone and single initial fetch/rebase.
- DONE: Verify six constructors already explicit and record production forwarding mechanism before edits.
- DONE: Original retry-property control red 2/3 on 5470aee6; new public regression assertion-red 0/1 on unchanged product; failing-test remote checkpoint precedes fix.
- DONE: Forward active count, initial delay and multiplier in FileCache.download; destination lock implementation untouched.
- DONE: New public regression green 3/3 ordinary and 3/3 property; ownership suite 3/3 ordinary repetitions and 3/3 property repetitions; malformed config 4/4; unchanged neighbors 6/6; independent multiplier control 1/1.
- DONE: Product-only reversal produces 1/1 intended assertion failure while preserving test and destination coordination bytes.
- DONE: Dedicated test and code reviews, required formatting, full existing-test edit ledger; all final native rows first attempt.
- DONE: PR source and why-first body updated, exact readback checked, DRAFT retained; no merge, readiness change, publication, deployment, production connection, model delegation, remote test submission, handoff or challenge-service operation.
- DONE: Final native evidence push and cleanup/process audit.


## Final status — 2026-10-17 03:22 UTC
OBSERVED: Final source d5eec8d10bf59305b4263bb96e741e45408c9f85 is on the updated OPEN/DRAFT PR https://github.com/CodexCoder21Organization/coursier/pull/5 . Gate is 35/35 positive first attempts; original property ownership control is red 2/3 at 5470aee6 -> green 3/3 each in three final runs; new regression red 0/1 -> ordinary/property each 3/3; independent multiplier 1/1; malformed config 4/4; neighbors 6/6. FileCache-only reversal is 1/1 intended assertion failure. Gate URL: https://github.com/CodexCoder21Organization/coursier/actions/runs/38998189119 .
OBSERVED: All native XML, full controlled-failure traces, exact-source/hash audit, workflow, format evidence, complete description, live PR metadata and process audit are retained on https://github.com/CodexCoder21Organization/coursier/tree/wip/coursierfetch4-evidence/.lane-evidence/coursierfetch4 . The stopped formatting candidate is retained separately and excluded from gate counts. No further verification batch was run after meeting the final gate.
OBSERVED: Both local commands and both Actions monitors have exited. Process audit finds no remaining lane-owned Java, build, watcher or timeout process and no held build slot. Compiled local run output is removed; invocation tools will be removed after the evidence push. Remote/local temporary workflow branches are deleted. Checkout source changes are committed; investigation files remain only on the evidence branch.
OBSERVED: A direct cleanup command was rejected by the local tool rule. Cleanup completed using Python after verifying the exact lane root, an ordinary directory beneath it, and zero tracked files under the generated output path. This did not change source or any gate.
INFER: Assigned product forwarding correction is complete and ready for orchestrator review. The orchestrator owns all PR CI/full-suite waiting and any subsequent integration decision. No merge, publish, deploy or readiness change occurred. Linux behavior is verified; no new platform claim is made.
Lessons: follow explicit settings through the final implementation call; force genuine public waiter events before releasing an owner; audit native rows and exact source instead of relying on workflow conclusions.
