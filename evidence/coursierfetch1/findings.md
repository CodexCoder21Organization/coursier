# coursierfetch1 findings

Started 2026-10-17 01:38 UTC; deadline 02:53 UTC.

## Plan
- IN PROGRESS: Read required guidance and retained traces; locate owning repositories.
- PENDING: Establish exact wait and deterministic public-API reproduction; checkpoint assertion-red test.
- PENDING: Fix owner, run three first-attempt positives and named neighbors, run product-only reversal.
- PENDING: Review tests and code, push source/evidence, open draft PR; clean temporary workflow and processes.

## Constraints
OBSERVED: No model delegation, remote test submission, publication, deployment, merge, handoff operation or challenge-service call is permitted. Only this lane checkout and findings are used. Native XML first attempts determine gates.

## FLAKES ENCOUNTERED
None in this lane yet.

## Challenges for the orchestrator
None yet.

## 01:41 UTC observation checkpoint
OBSERVED: Required Testing Architecture sections and Philosophy sections read; kompile-core README and BuildTestRunner README read. Repository search located kompile-core (manager artifact), CoursierInterface and coursier. No root AGENTS.md exists in the two inspected trees.
OBSERVED: All three retained traces contain only three btr worker stacks, truncated to eight frames. They end at FutureTask.get -> CoursierProgressDeadline.await -> fetchWithProgressAwareBackstop. There are no inner fetch-worker, socket, HTTP, or file-lock frames, so the exact wait cannot be named from this evidence.
OBSERVED: kompile-core source already configures socket connect/read limits of 15/60 seconds and a 900-second whole-fetch inactivity guard. The scenario checks its listener after 60 seconds. A 60-second caller wait does not establish an unbounded fetch. The socket limit uses JDK global defaults read once when the handler initializes; the existing stalled-repository test starts a fresh JVM with those defaults supplied at launch and does not cover earlier handler initialization. That is an investigation lead, not yet the cause of the retained run.
OBSERVED: Diagnostic-only source expands the same scenario's frame capture to bounded-coursier and coursier workers with 32 frames. No timeout, caller count or assertion changed. Checkpoint: https://github.com/CodexCoder21Organization/BuildTestRunner/commit/0d27952c335641435a76599d1f6500dfd4f3387c on wip/coursierfetch1-diagnostic. Throwaway workflow branch wip/gha-run/coursierfetch1 runs three isolated jobs on that exact source. This run gathers missing evidence; it is not a positive gate or a claimed failing-test milestone.

OBSERVED: Diagnostic run is https://github.com/CodexCoder21Organization/BuildTestRunner/actions/runs/38991888268 (three jobs, exact source 0d27952c335641435a76599d1f6500dfd4f3387c). Existing diagnostic edit ledger: the stack filter now includes names containing coursier as well as btr; retained frame depth increases from 8 to 32. Only diagnostic evidence changes; no scenario assertion or bound changes.
OBSERVED: CoursierInterface default branch is use-forked-coursier; Coursier cache fork is CodexCoder21Organization/coursier. Read CoursierInterface README and ConnectionBuilder/CacheUrl/Downloader paths. These pass a custom URL handler through to JDK connections and do not set per-connection timeouts. WorkspaceCoursierCache wraps ordinary JDK HTTP connections and forwards timeout setters if called. No inner wait is yet evidenced.

## 01:44 UTC native diagnostic progress
OBSERVED: Run 38991888268 has native first-attempt successes for diag1 (25400ms) and diag3 (45591ms); prerequisite builds succeed and there is no attempt-history. diag2 is still active. These are diagnostics, not proof of a fix. Artifacts are under coursierfetch1-diagnostic-results in this lane output directory.
OBSERVED: git diff from the manager 0.0.205 release commit 4b7108d9ccd00e2dd637258317f7ad8632134392 to core main 097071aa is empty for CoursierNetworkTimeouts.kt, CoursierProgressDeadline.kt and WorkspaceCoursierCache.kt. Current source is therefore the same relevant implementation as the retained runner evidence.
OBSERVED: Original full native XML and log have no Maven retry warning, socket-limit failure, or repository URL diagnostic. All three failed attempts are scenario-first 60-second waits followed by cleanup waits; their 120-second lifetime is shorter than the configured whole-fetch bound. This does not yet name the inner wait.
OBSERVED: Existing public timeout behavior is being checked separately in https://github.com/CodexCoder21Organization/kompile-core/actions/runs/38992060180 against exact main 097071aa, with the unchanged silent-repository ManualClock test and launched-JVM socket-limit test. This diagnostic branch is wip/gha-run/coursierfetch1 in kompile-core; no production test submission occurred.

## 01:46 UTC evidence checkpoint
OBSERVED: Partial source reading and completed native diagnostic evidence are pushed at https://github.com/CodexCoder21Organization/kompile-core/commit/81a7a12b on https://github.com/CodexCoder21Organization/kompile-core/tree/wip/coursierfetch1-evidence . This evidence branch is separate from any proposed product change.
OBSERVED: A tracked 22-minute monitor owns the two raw GitHub Actions diagnostic runs, reads status at 120-second cadence, recognizes every completed conclusion and command failure, and downloads native artifacts. build-watchman supports PR or buildtest identifiers rather than raw Actions identifiers, so the watch-build skill's raw Actions endpoint and bounded-wait guidance are used. No PR CI watcher exists.
OBSERVED: No product edit has been made. No assertion-red reproducer or fix gate can be claimed until the inner worker frames establish the condition. The retained child's launch receives finite JDK socket-limit properties explicitly, so earlier JDK handler initialization is not currently supported as an explanation for this run.

## 01:48 UTC exact wait established
OBSERVED: Expanded run 38991888268 diag2 is green only after retry: its first attempt fails at the original listener assertion and has an attempt-history; the final 48301ms row must not be counted as a clean success. Native first failure shows one actual worker sleeping at Thread.sleep -> coursier.cache.internal.Retry.loop -> Retry.retryOpt -> Downloader.downloading -> Downloader.Blocking.remote. The Java fetch worker waits in scala.concurrent.Await.result; all other captured download workers are idle. There is no socket read in the active download path.
OBSERVED: Downloader.downloading wraps each download in CacheLocks.withUrlLock(url), a JVM-global ConcurrentHashMap keyed only by URL, regardless of cache directory. If another cache owns that URL, checkDownload checks the waiter's different local file (which the owner never writes), returns None, and Retry.retryOpt doubles its sleep on every None without stopping at Retry.count. The active worker can therefore remain asleep long after the other cache's lock holder has completed; the captured all-idle peers are consistent with this path.
INFER: The mechanism to reproduce is cross-cache URL coordination with growing polling delay. A deterministic public FileCache test will hold the first cache's repository response and require a second independent cache to fetch the same URL before that first response is released. This forces the incorrect cross-cache dependency directly, rather than waiting for a particular scheduling distribution.
OBSERVED: Existing bounds run 38992060180 passes both named tests on their first attempts, no attempt-history: silent-repository backstop 1895ms; launch-time socket bounds 9348ms; prerequisite succeeds. This refutes a missing generic silent-network backstop, while leaving the identified cache coordination defect to fix in its owner, CodexCoder21Organization/coursier. No timeout increase or new retry is planned.

## Ownership invariants before any product edit
| Resource | Creator / owner | Terminal states and public behavior | Public test |
| --- | --- | --- | --- |
| Download into one cache location | FileCache caller / cache destination | One owner publishes that destination; overlapping readers of that same file receive the same complete file. No extra HTTP body. | same location joins its owner |
| Equal URL in distinct cache locations | Each caller / its own cache destination | Each cache can finish while the other is held; neither owns the other's response, file or error. | independent locations |
| Error in a different cache location | Failed caller owns its own error | A 500 held for one cache cannot block another cache's successful same-URL observation. Its error stays with its own caller. | independent locations after an owner error |
OBSERVED: Candidate owner is coursier fork main b051ebea6dcadacf3f781687c1a97e89f4eb69f9. It already contains the buffer-cost changes and its download coordination matches wip/LH7-download-buffer. Fresh owner tree uses Mill/uTest, not kompile scripts; native Mill XML will determine its gates.
OBSERVED: Test-only checkpoint pushed to wip/coursierfetch1-work in coursier. It holds one real loopback HTTP response, with no machine-dependent triggering, and asserts another cache's request and complete response occur before release. Product remains unchanged. Three public lifecycle rows above are covered, including same-cache and error boundaries. No existing test is edited.

## 01:51 UTC test-only owner checkpoint
OBSERVED: Owner test suite is FileCacheLocationOwnershipTests, using real FileCache.file(Artifact) against an owned loopback HttpServer. Both independent-location cases hold the first response until the other cache has returned complete bytes. The same-location case uses a canonical path alias, keeps one HTTP response and checks identical complete output. Each invocation owns and closes its HTTP server, thread pools and temporary paths.
OBSERVED: Test-only source https://github.com/CodexCoder21Organization/coursier/commit/7fc738db54f6f2400daac2504dd0f80c8ef68ad8 is pushed to https://github.com/CodexCoder21Organization/coursier/tree/wip/coursierfetch1-work . No product edit exists. The first workflow submission was cancelled after detecting a source-generation typo in the new test's time-unit identifiers, before any valid native test verdict; it is not a red reproduction. The corrected test-only workflow is submitted separately. No existing test is edited.
OBSERVED: Source review narrows the planned fix to the ownership key: a canonical destination path distinguishes independent caches while preserving the same-file owner. It removes the incorrect cross-cache wait rather than adjusting the 900-second inactivity limit or changing Retry policy.

## FLAKES ENCOUNTERED — diagnostic dependency wait
OBSERVED: https://github.com/CodexCoder21Organization/BuildTestRunner/actions/runs/38991888268 diag2 first attempt fails at `Close must stop B execution before its real batch is entered.` after 120888ms including cleanup. A later 48301ms row succeeds, so no clean diagnostic gate is claimed. Native failure shows the global-URL lock retry sleeping, one Scala Await fetch waiter, and otherwise idle captured download workers. Complete native XML and frames are in coursierfetch1-diagnostic-results/coursierfetch1-diagnostic-diag2 and will be retained on the evidence branch. This is the upstream condition being reproduced, not a green first attempt.

## 01:53 UTC owner build setup
OBSERVED: https://github.com/CodexCoder21Organization/coursier/actions/runs/38992597302 has no native XML rows and therefore no verdict. Mill compiles its build definition, then CoursierJavaModule.javacSystemJvm cannot start `cs` (error 2, no such file). The throwaway workflow omitted the coursier/setup-action used by this repository's existing CI. Added that existing setup action to the throwaway workflow; product and tests are unchanged. This is a corrected diagnostic setup, not a valid red test or positive gate. Complete log is retained under coursierfetch1-red-results.

## Challenges for the orchestrator — 01:56 UTC
OBSERVED: The throwaway workflow with coursier/setup-action@v1.3 receives GitHub conclusion startup_failure before any job exists, with no available job log, artifact or check-run diagnostic. The tag exists and its action uses node20; the rejection's precise platform reason is not exposed. No verdict is claimed. The workflow now installs the exact checksummed Coursier launcher already pinned in kompile-core scripts, directly into the GitHub job's temporary PATH under the required `cs` name. This avoids dependence on that action while preserving the repository's real Mill build. User instructions prohibit a report-challenge call, so this platform issue is recorded here only.
