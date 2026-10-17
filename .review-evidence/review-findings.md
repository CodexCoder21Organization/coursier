# revcoursier1 independent review

Target: https://github.com/CodexCoder21Organization/coursier/pull/5 at 91c1001b4191da7c0842f41d559dab5f2afb2c5f.
Started approximately 2026-10-17 02:24 UTC; 40-minute limit.

## Plan
- DONE: Read earlier lane findings, required Testing Architecture and Philosophy sections, fork README; no root AGENTS.md exists. Locate fork with github-repos skill; fresh clone, exact head and initial rebase (unchanged head).
- DONE: Review ownership coordinator, same-location serialization, remaining polling behavior, and test coverage.
- DONE: Run new suite locally under build slot; inspect native XML.
- DONE: Verify retained native baseline/gate evidence and report review findings. PR unchanged.

## Initial observations
OBSERVED: Diff has four files, 237 additions and one deletion. README describes same-location ownership; CacheLocks adds a canonical-path adapter; Downloader switches its existing coordinator to that adapter; new FileCacheLocationOwnershipTests has three scenarios. No existing test, retry implementation, operation bound, dependency pin or release version changes.
OBSERVED: getCanonicalPath resolves dot segments and existing symbolic-link parents. The same-location guard uses first/../first; it does not use a symbolic link. The remaining retryOpt path still doubles sleep for None regardless of retry count. Its caller-contract implications and whether the guard really forces overlap are under review.
OBSERVED: Prior findings report native red 2/2 and positives 15/15 plus product-only reversal at the exact target. These counts are not yet independently audited here.

## FLAKES ENCOUNTERED
None observed in this lane.

## Challenges for the orchestrator
OBSERVED: The fork build definition invokes executable cs, while this box provides coursier. A checkout-local cs link will be supplied on PATH for the unchanged Mill build. The root README does not document the test command; existing build/CI and the prior lane identify ./mill -i cache.jvm[2.13.16].test.testOnly coursier.cache.FileCacheLocationOwnershipTests.

## 02:29 UTC — native evidence audit
OBSERVED: Downloaded native artifacts from https://github.com/CodexCoder21Organization/coursier/actions/runs/38993111192 and https://github.com/CodexCoder21Organization/coursier/actions/runs/38993509459. Independent XML parsing confirms red baseline 2/2 affected assertions, successful same-location guard; exact-head positives 15/15 (three new cases x3 plus six neighbors); product-only reversal repeats both intended assertion failures while the guard passes. No skipped or attempt-history element exists. Exit statuses match the XML. SHA-256 checks match candidate files for all positive jobs and baseline product files for the reversal. Audit output: revcoursier1-native-audit.json beside these findings.
OBSERVED: Red source proof is 21a761e083738ea47f334a53f045257dbd5b5e27; final/control source proofs are the target full SHA. The red assertions are secondEntered.await(30, TimeUnit.SECONDS), not compilation failures. No need to re-derive the previously established mechanism.
OBSERVED: Local exact-head Mill run holds a build slot, has compiled its nested build definition and is compiling the repository build definition. It has no native test rows yet, so no local verdict is claimed.

## Ownership review table
| Resource | Ownership / observable contract | Review evidence |
| --- | --- | --- |
| Independent destination | Its own coordinator and response; another cache response/error must not delay it | Canonical keys differ; both new public cases return complete exact bytes before first release; baseline/control return assertion failures |
| Same destination | One publisher; complete file shared by callers | Same canonical string selects existing acquisition/finally removal; OS file locking and atomic move are unchanged; guard coverage strength still being checked |
| Lock entry | Acquirer creates it and removes it on every exit | Existing putIfAbsent / try-finally removal unchanged, including exceptions; no retained entry introduced |
| Path aliases | Same effective destination selects same coordinator | getCanonicalPath used; dot alias in guard is already normalized by CachePath.localFile before this new helper; no actual symbolic-link alias or relative-versus-absolute pair is tested |
| Same-file waiter | Polls its own destination and eventually observes publication | Retry.retryOpt still grows delay for None without consulting count; FileCache Task has no deadline or cancellation API; this is unchanged existing behavior, separate from independent-cache ownership |

INFER: The product change removes the evidenced cross-cache dependency without changing same-destination publication mechanics. It does not fix growing same-file polling waits or make FileCache intrinsically time-bounded. Any claim that this PR establishes a general download deadline would exceed its evidence; its current description correctly says retry behavior is unchanged.

## 02:31 UTC — fixture input experiment planned
OBSERVED: All six newly added FileCache constructions omit the credentials constructor argument. FileCache's default argument evaluates CacheDefaults.credentials, which reads the user-selected Scala configuration file during construction; later with-policy/with-retry calls cannot prevent default-argument evaluation. CacheEnv.credentialsFromConfig throws when that file cannot be parsed. This can affect the loopback scenarios even though they need no user configuration.
INFER: An owned malformed Scala configuration file selected only for the warm local test invocation should change the new suite's result without changing the product or test source. Planned experiment: first retain native XML for the unchanged exact head, then select the owned file for a second targeted invocation and inspect its native rows. A build-only failure would be no verdict and would not establish a test finding.
OBSERVED: Same-location guard coverage has two source-level gaps: downloadingArtifact runs before Downloader.downloading attempts either coordinator, so releaseOwner can let the first publication finish before the second actually contends; first/../first is normalized by CachePath.localFile before the added canonical-path helper is called, so it is not a distinct lock-key alias. Suggested strengthening is a real symbolic-link or relative/absolute alias plus an observable public logger event from checkDownload while the owner remains held. No source change has been made here.

OBSERVED: A small standard-Java path experiment on this box confirms getCanonicalPath maps an existing symbolic-link parent and relative/absolute spellings to the same string, even when the artifact subdirectory does not yet exist. It used only owned temporary paths, removed afterward. Case-alias behavior on a case-insensitive filesystem cannot be established on this Linux overlay filesystem; no cross-platform result is claimed. The fork's public README and CI point to coursier/coursier as upstream, rather than the square/coursier name in the brief.

## 02:33 UTC — exact-head local result and isolated configuration check
OBSERVED: ./mill -i cache.jvm[2.13.16].test.testOnly coursier.cache.FileCacheLocationOwnershipTests completed at the target source with native XML 3/3, no history/skipped/error rows: independent success 0.739s; independent owner error 0.044s; same location 0.072s. XML and complete log retained under .review-evidence/local-clean. Product and test source remain unchanged.
OBSERVED: Selecting malformed config through the whole Mill process stops Mill's own dependency configuration before test execution. That invocation provides no new test rows and is not a test verdict. The earlier success XML was preserved before the experiment.
The mechanism being checked is default-argument evaluation reading unrelated user config before the fixture's explicit setters, and the evidence sought is native failure rows in the unmodified new suite when only its forked JVM receives an owned malformed config file.
OBSERVED: To isolate that condition from Mill's own configuration, the next experiment temporarily adds one forkArgs property in the cache test build module. It changes no product/test source, assertion or time bound and will be restored immediately afterward. The exact temporary build-only diff and native results will be evidence only, never pushed to the PR.

## Review findings

### P2 — Construct the new caches without reading user configuration
Location: https://github.com/CodexCoder21Organization/coursier/blob/91c1001b4191da7c0842f41d559dab5f2afb2c5f/modules/cache/jvm/src/test/scala/coursier/cache/FileCacheLocationOwnershipTests.scala#L50 (also lines 55, 118, 123, 187 and 192).
OBSERVED: All six constructors omit the credentials argument, so FileCache.apply evaluates CacheDefaults.credentials before any with-setting call. That reads the user-selected Scala config file. A malformed unrelated file prevents these otherwise self-contained loopback tests from running their download assertions.
OBSERVED: Exact-head ordinary local run has native XML 3/3 successes. The controlled run, with only the test JVM receiving -Dscala-cli.config=<owned-file-containing-a-single-opening-brace>, has native XML 0/3: all three rows fail with java.lang.Exception, message beginning `scala.cli.config.ConfigDb$ConfigDbFormatError: Error parsing config DB`. Frames name CacheEnv.credentialsFromConfig:124 -> CacheEnv.defaultCredentials:118 -> CacheDefaults.credentials:106 -> FileCache.apply:38 -> new fixture lines 50, 118, 187. No test source or product byte changed. Complete error text and traces are retained in local-ambient-fork/test-report.xml and test.log on the evidence branch.
INFER: Test results depend on a user config file unrelated to the owned loopback server, contrary to the required hermetic-test contract. Supply credentials = Nil directly in every constructor; a later noCredentials setter still evaluates the default first and is insufficient. Supplying the fixture's other explicit settings directly also avoids unnecessary default evaluation. The review does not request changing FileCache's intended default behavior for real callers.

### P2 — Make the same-location guard reach the waiter path before owner release
Location: https://github.com/CodexCoder21Organization/coursier/blob/91c1001b4191da7c0842f41d559dab5f2afb2c5f/modules/cache/jvm/src/test/scala/coursier/cache/FileCacheLocationOwnershipTests.scala#L197 .
OBSERVED: The guard releases owner immediately after downloadingArtifact signals. Downloader.remote invokes that callback at line 425, before Downloader.downloading at line 430 and before either coordinator is attempted. Therefore the latch proves that the second operation began, not that it reached the same-file waiter branch. Also, CachePath.localFile normalizes the first/../first root at line 94 before computing the destination; the guard presents the same path string to the new helper, rather than exercising its canonical-path handling.
INFER: A legal schedule pauses the second caller immediately after the logger signals, lets the test release the owner and the first publication finish, then resumes the second caller. The second sees the completed file through proceed and returns without a second GET; every guard assertion passes without exercising concurrent ownership. This leaves the original same-destination serialization purpose insufficiently protected by the registered opposite guard. Use a real distinct path spelling (relative versus absolute, or an owned symbolic-link parent) and wait for an event inside the waiter path while the first response is held, such as the real server receiving its HEAD request or the gettingLength logger callback. Retain the exact one-GET and complete-byte assertions. This is a test-coverage finding; no same-destination product regression was observed.

## 02:35 UTC — final experiment accounting
OBSERVED: Ordinary exact-head local command exits zero with 3/3 native rows and no skipped/history elements. Supplemental configuration-only fork exits one with 3/3 native error rows and no skipped/history elements. All source/test/README bytes and the restored build module match the target SHA. The temporary build change only supplied the owned config property to the test JVM; it is restored, with its diff retained separately as evidence. The first whole-process config attempt had no test verdict because Mill itself read that file; it is explicitly excluded from counts.
OBSERVED: The required original red-first 2/2, exact-head 15/15 positives and product-only reversal 2/2 were independently audited from their native artifacts, including source proofs and file hashes. No extra fix-gate batch, remote submission, CI monitor or original-mechanism re-derivation occurred.

## FLAKES ENCOUNTERED — controlled new-fixture configuration defect
OBSERVED: The first isolated-config attempt reaches the actual new tests and all three fail before the intended latch assertions. This is a deterministic fixture-input defect, not an intermittent failure: exact error prefix and frames are recorded in finding one, with full native XML and log retained. No run URL exists because this is a local test invocation. The build-only earlier attempt has no verdict and is not counted. No existing test or assertion was changed or weakened, and the review brief prohibits editing the PR, so these findings are handed to the orchestrator with a complete reproduction.

## Brief completion audit
OBSERVED: DONE — required named reading and earlier findings read; fresh fork checkout, configured identity, exact head and one initial rebase; native retained baseline/gates verified rather than re-derived.
OBSERVED: DONE — inspected original same-file ownership, acquisition/finally-release and unchanged OS-file publication; reviewed canonical spelling on Linux and identified missing alias/overlap test coverage. Case-insensitive filesystem behavior is not verified on this Linux box.
OBSERVED: DONE — inspected JVM-global coordination, per-root structure coordination and shared default pool. No additional URL-wide network ownership remains in Downloader. The same-file Retry loop remains unbounded for None; FileCache's Task API has no intrinsic deadline/cancellation. This is pre-existing behavior preserved by the diff and is recorded as a limit, not counted as an introduced defect.
OBSERVED: DONE — all four diff files reviewed; no existing test, assertion, time bound, sleep, product retry implementation, dependency or version is changed. New fixture retry settings equal the defaults. Production diff is minimal and understandable for coursier/coursier upstream; no platform-specific workaround or consumer change is added.
OBSERVED: DONE — both new cases and the opposite guard run locally, native XML 3/3. A separate isolated input experiment establishes finding one without changing product/test source. Two test findings; no observed product regression in the reviewed paths.
OBSERVED: PENDING — durable review evidence push and final process/slot audit. No merge, queue, publication, deployment, readiness change, PR edit/comment, handoff operation, challenge-service call, model subprocess or remote test submission occurred.

## Final review state
OBSERVED: Two P2 findings concern the new tests: inherited user configuration changes their result, and the same-location guard does not force actual contention or exercise a distinct destination spelling. No P1/P3 finding and no observed product defect in the reviewed change. Recommended disposition is to address both test findings before treating the promised coverage as complete.
OBSERVED: Evidence and native XML are durable at https://github.com/CodexCoder21Organization/coursier/tree/wip/revcoursier1-evidence/.review-evidence . First checkpoint: https://github.com/CodexCoder21Organization/coursier/commit/2941f395 . This separate evidence branch is not the PR branch; it contains no product change relative to the reviewed source.
OBSERVED: Final read confirms https://github.com/CodexCoder21Organization/coursier/pull/5 remains OPEN and draft at 91c1001b4191da7c0842f41d559dab5f2afb2c5f. No PR edit or comment was made. All tracked source, test, README and build bytes match that head after the temporary test-fork setting was restored.
OBSERVED: Final ps audit finds no lane-owned background Java, shell or timeout process. Both test invocations released their build slots. Temporary launcher link, run script and owned config input are removed; their necessary reproduction material is retained on the separate evidence branch. All plan items are done. No CI wait was started.

REVIEW 91c1001b4191da7c0842f41d559dab5f2afb2c5f: FINDINGS 2 (P1 0, P2 2, P3 0)
