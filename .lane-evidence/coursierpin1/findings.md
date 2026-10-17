# coursierpin1 findings
Started 2026-10-17 03:34 UTC; deadline 04:49 UTC.

## Plan
- IN PROGRESS: Verify merged source, owning consumer pins, publication convention and required guidance.
- PENDING: Build merged Coursier, compare old/new API and forwarding, publish next version.
- PENDING: Public consumer red test, dependency adoption, three first-attempt gates and resolution neighbors, old-pin control.
- PENDING: Dedicated reviews, draft pin PR, evidence checkpoint and cleanup.

## Required reading
OBSERVED: Read github-repos and publish-maven-artifact skills; Testing Architecture sections Test-Driven Development, Never Calibrate a Reproducer to a Machine, Tests Must Be Hermetically Sealed, Test Verdicts Must Be Hardware-Independent; Engineering Philosophy sections Reproduce a Bug Before You Fix It, A Refuted Hypothesis Is a Successful Outcome, Never Hide a Failure. Read coursierfetch1 findings and consumption notes. Reading project guidance next.

## Initial verification
OBSERVED: https://github.com/CodexCoder21Organization/coursier/pull/5 is MERGED at 7434805e35d9941f840c6e8c914f7d53fe852e10.
OBSERVED: GitHub lookup of CodexCoder21Organization/kompile returns repository not found. Organization search locates kompile-core and kompile-cli; inspecting those actual consumers.
OBSERVED: Existing upstream evidence records two deterministic public assertion failures, 15/15 positive first attempts and product-only reversal at https://github.com/CodexCoder21Organization/coursier/actions/runs/38993509459 . This lane adopts that already merged fix; it does not rewrite the owner fix.

## FLAKES ENCOUNTERED
None so far.

## Challenges for the orchestrator
OBSERVED: Task link names a nonexistent kompile repository; actual module repository discovery is in progress.

## 03:38 UTC mechanism and publication plan
The mechanism is JVM-global URL coordination across distinct cache destinations, compounded by FileCache.download omitting its instance retry count and backoff arguments; the evidence that demonstrates it is the merged source and native public owner tests at https://github.com/CodexCoder21Organization/coursier/actions/runs/38998189119 (35/35 first attempts, fix-only assertion-red control).
OBSERVED: Fork publishes under io.get-coursier:coursier-cache_2.13 at kotlin.directory, not a separate group. Both consumer modules pin 2.1.31-buffer2. Prior publication recipe is checkpoint/LH7-buffer-only-release.md on the owner evidence branch: thin Mill JAR with previous POM and only its root version changed. Previous POM keeps cache-util/paths/util at 2.1.30. Next candidate version is 2.1.31-buffer3.
OBSERVED: Merged-main Mill thin build is running under an acquired local slot with a 25-minute hard deadline. No product edits. README files read in coursier, kompile-core and kompile-cli; no root AGENTS.md in those checkouts.
INFER: Consumer adoption must update test artifact annotations and prepared-repository coordinate literals as well as the two direct build pins, to exercise one cache version consistently. Those edits change version literals only, never assertions.

| Resource | Owner and terminal behavior | Public check |
|---|---|---|
| Two workspace caches | Each LocalBuildWorkspace owns a distinct cache until close | Hold first artifact body; second workspace must resolve complete bytes before first release |
| HTTP fixture | Test owns server and executor; release held response on every exit | Await executor termination and close both workspaces in finally |
| Shared artifact destination | Existing upstream canonical ownership unchanged | Merged upstream tests verify one shared GET and identical complete files |

## Build setup findings
OBSERVED: Initial thin build exited before source compilation: build helper runs cs java-home but this host supplies only coursier; version helper runs git describe but fork clone has no v* tags. Exact messages: Cannot run program "cs" ... No such file or directory; fatal: No names found, cannot describe anything. Full trace retained in lane log. Created lane-local bin/cs pointing at the authorized launcher and fetched upstream v2.1.30 solely for version metadata; product source is unchanged. Build is restarted under a fresh slot.
OBSERVED: Consumer test-only source is checkpointed at https://github.com/CodexCoder21Organization/kompile-core/commit/74685e75 on wip/coursierpin1-work. Baseline gate uses a workflow-only branch and exact candidate checkout. It exercises WorkspaceCoordinateResolver against an owned loopback repository, holds the first JAR response and requires complete second-workspace bytes before release. No product pin changed yet.

## Gate setup correction
OBSERVED: Baseline https://github.com/CodexCoder21Organization/kompile-core/actions/runs/38999497697 stopped at checkout, before executing any test: the workflow ref used a short commit rather than the required full SHA. This is no verdict. Replaced the checkout ref with the full immutable candidate SHA; the test and product source remain unchanged. Corrected the workflow generator for subsequent gates.
OBSERVED: Previous build-tag attempt requested v2.1.30, absent from upstream. Fetching upstream v2.1.24 brought reachable release tags successfully; lane-local cs alias now works and the real cache module is compiling. No release tag was created or pushed.

## 03:40 UTC publication readiness
OBSERVED: Merged main thin JAR built successfully. Old/new inventory is 176/176 classes, zero removed. javap bytecode confirms FileCache.download reads retry(), retryBackoffInitialDelay() and retryBackoffMultiplier() and passes them to Downloader.apply; canonical lock bytecode calls File.getCanonicalPath. Public declaration comparison is retained in artifacts/.
OBSERVED: New JAR SHA-256 f174277994ab1d62d4e4d6175215106ccc14ad37e8d9cd465752b1763aa1f534; old buffer2 JAR SHA-256 20307f9eabb1532b8630fcb0ade84c990fd11c175afebac7b0465c471a4d2406. Publication uses exact main 7434805e35d9941f840c6e8c914f7d53fe852e10, previous POM with root version only changed to 2.1.31-buffer3, unchanged dependency versions, and matching main source archive. Publication of this named artifact is explicitly authorized in the task.
OBSERVED: Full-SHA baseline https://github.com/CodexCoder21Organization/kompile-core/actions/runs/38999577466 stopped before tests at git's container ownership check. Standard safe.directory setting added only to temporary workflow. This is no verdict, not a test failure.
OBSERVED: javap comparison preserves every class and every non-synthetic public declaration. Three generated $anonfun helper signatures differ; javap -v marks all three ACC_SYNTHETIC. They correspond to changed private coordination/closure bodies, not declared public API. Full declaration/flag outputs retained. Artifact class-list superset gate is met.
OBSERVED: Accepted baseline workflow starts at https://github.com/CodexCoder21Organization/kompile-core/actions/runs/38999673414 . The tracked watcher uses a 40-minute deadline, 120-second cadence, every terminal conclusion, and native artifact download. build-watchman skill was read; its CLI supports PR/buildtest identifiers, not this raw targeted Actions gate.

## 03:41 UTC publication milestone
OBSERVED: Publisher CLI exited zero and reported io.get-coursier:coursier-cache_2.13:2.1.31-buffer3 published at https://kotlin.directory/io/get-coursier/coursier-cache_2.13/2.1.31-buffer3/ . Downloaded JAR and POM compare byte-for-byte with staged publication. Published JAR SHA-256 is f174277994ab1d62d4e4d6175215106ccc14ad37e8d9cd465752b1763aa1f534.
OBSERVED: The first helper-flag audit script omitted Downloader$ and stopped on its third helper. The corrected complete javap -v run confirms ACC_SYNTHETIC on all three changed helpers. This corrects the timing of the earlier statement: complete helper verification finished immediately after publication; class-inventory verification and forwarding verification finished before publication.
OBSERVED: Publication source stays exact merged main. Previous POM dependency versions are unchanged. No consumer artifact is published and no production host is accessed.
