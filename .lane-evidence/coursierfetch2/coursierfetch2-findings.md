# coursierfetch2 findings
Started 2026-10-17 approximately 02:33 UTC; deadline 03:18 UTC.

## Plan
- DONE: Read prior findings, required Testing Architecture and Philosophy sections, fork README; fresh clone, identity and initial fetch/rebase.
- DONE: Verify malformed-config baseline at 91c1001b; harden six constructors and the real same-file waiter guard.
- DONE: Run native three-run gate, config check, previous-product control and neighbors; audit first attempts. Requested same-file red criterion is REFUTED by the old product passing it.
- DONE: Checkpoint evidence, review test changes, update draft PR body and finish process audit.

## Required reading
OBSERVED: Read Testing Architecture sections Test-Driven Development, Never Calibrate a Reproducer to a Machine, Tests Must Be Hermetically Sealed, Test Verdicts Must Be Hardware-Independent; Philosophy sections Reproduce a Bug Before You Fix It, A Refuted Hypothesis Is a Successful Outcome, Never Hide a Failure; coursier README; coursierfetch1 and revcoursier1 findings including both named P2 findings. No root AGENTS.md exists.

## Mechanism and pre-edit evidence
The mechanism is that six fixture constructors evaluate CacheDefaults.credentials before their explicit setters, reading unrelated user config; the evidence that demonstrates it is the review native 0/3 malformed-config result and the verified FileCache default-argument path. This lane will repeat the isolated JVM control at the exact reviewed head before changing the fixture.
OBSERVED: The guard signals downloadingArtifact before attempting ownership. CachePath normalizes its dot alias before lock selection. Replacing this with a real symbolic-link parent and downloadLength(watching=true) will force an actual same-file wait before owner release.

## Course-changing finding: requested same-file red control
OBSERVED: Both callers in the same-file guard have exactly equal artifact.url values. Previous product uses CacheLocks.withUrlLock(url); equal URLs therefore select the same lock, independent of the spelling of the destination path. Its ifLocked branch calls checkDownload, just as the current destination coordinator does when a same-file owner exists.
INFER: The strengthened same-URL guard should pass both previous and current product. A requirement that this guard fail on previous URL ownership contradicts the contract it tests. The original independent-location cases distinguish old/new ownership and remain the appropriate red controls. This lane will retain the exact previous-product experiment and report the result rather than add an assertion about internal lock selection.

## Invariants
| Resource | Owner and observable behavior | Test |
| --- | --- | --- |
| Fixture config | Each constructor supplies its own credentials, policy, TTL, pool and retry settings | Entire suite with malformed unrelated config selected only in test JVM |
| Same destination through symlink | First caller owns publication; second reaches watching=true while owner response stays held; both receive exact byte 1; one GET | Strengthened same-location guard |
| Distinct destinations | Each owns its response and output, even when the other response/error stays held | Existing independent cases retained unchanged apart from constructor inputs |

## FLAKES ENCOUNTERED
None yet.

## Challenges for the orchestrator
OBSERVED: Same-file red-first requirement is inconsistent with the previous equal-URL coordinator, as explained above. No product change is expected or proposed. No remote test submissions, model delegation, deployment, publication, merge, handoff operation or challenge-service call is permitted.

## 02:40 UTC checkpoint
OBSERVED: Isolated-config baseline workflow is running at https://github.com/CodexCoder21Organization/coursier/actions/runs/38995746243 , exact checkout 91c1001b4191da7c0842f41d559dab5f2afb2c5f. Only the forked test JVM receives the malformed file property via an evidence-only build-module edit. No fixture or product source has been edited yet.
OBSERVED: Candidate test file is prepared outside the repository until the baseline has native rows. Six constructors receive explicit credentials=Nil and policy/checksum/pool/TTL/redirection/retry settings directly; same-location alias uses an owned symlink and waits for the public downloadLength callback with watching=true. Existing exact-byte and single-GET assertions are retained, with additional pre-release pending-future and count assertions.
OBSERVED: Initial rebase left source unchanged at the reviewed SHA; main remains b051ebea6dcadacf3f781687c1a97e89f4eb69f9. The current draft remains OPEN. No product change is planned.

OBSERVED: A second brief mismatch is the suggested pre-fix revision: 91c1001b~1 is 519bb1d0 and already contains the destination coordinator. The real URL-based product is b051ebea6dcadacf3f781687c1a97e89f4eb69f9 (the round-1 registered control). The control workflow restores CacheLocks.scala and internal/Downloader.scala from that exact main revision; no test bytes are reversed.

## Dedicated test review before the gate
OBSERVED: The candidate uses FileCache.file and localFile plus public CacheLogger callbacks, with owned loopback HTTP servers and owned temporary paths. No mock, reflection, product edit or private API is used. Every constructor now supplies all configuration-sensitive settings before evaluation; constant defaults and ordinary clock creation do not select fixture policy. Symbolic-link and actual parent paths are asserted distinct before canonicalization and equal after it. The owner response remains blocked until the waiter emits watching=true, with both futures still pending and exactly one GET before release. Full-byte, one-GET, error-text and distinct-destination assertions remain unchanged.
OBSERVED: Cleanup releases the owner even after an assertion failure, stops the owned server and thread pools, waits for their existing termination bounds, and removes only the owned temp directory. The symlink points into that same owned directory. None of the six prior test bounds or retry/iteration values has changed.

## Dedicated implementation review
OBSERVED: No product or dependency change is needed for either P2 finding. The fixture-only diff replaces chained post-construction setters with equivalent constructor values and credentials=Nil. The same-location test swaps its already-normalized dot path for a symlink and replaces the early downloadingArtifact signal with downloadLength(watching=true), which is called only by checkDownload. Both old and new product call that same-file wait branch when URLs and destinations coincide; a URL-based negative control is therefore expected to preserve this guard.

## Existing-test edit ledger
- FileCacheLocationOwnershipTests, all three scenarios: six constructors previously inherited credentials and evaluated defaults before chained setters; now use credentials=Nil and equivalent explicit constructor policy/checksum/pool/TTL/redirection/retry values. This removes unrelated configuration input without altering assertions or operation bounds.
- same location joins its owner: first/../first becomes a real owned symlink; assert distinct lexical destinations and equal canonical destinations. downloadingArtifact latch becomes downloadLength(watching=true), followed by added assertions that both futures are pending and the GET count is one before release. Existing complete-byte, canonical-output, exact-count and cleanup assertions remain intact.
- No other existing test is edited.

## 02:43 UTC red baseline milestone
OBSERVED: https://github.com/CodexCoder21Organization/coursier/actions/runs/38995746243 has three native unsuccessful rows at exact reviewed source 91c1001b4191da7c0842f41d559dab5f2afb2c5f, all ConfigDb$ConfigDbFormatError from the owned malformed file selected only in the test JVM. Native baseline is 0/3, with no prior-attempt element. No source fixture or product edit preceded this proof. The workflow conclusion is success because evidence upload intentionally retains the test command's separate exit code; only XML is used for the verdict.

## 02:44 UTC source checkpoint and final registered selection
OBSERVED: Native red evidence is durable at https://github.com/CodexCoder21Organization/coursier/commit/29aeb5352cc71a43b4e45513d176a086a53e84ae on wip/coursierfetch2-evidence. Fixture-only candidate is https://github.com/CodexCoder21Organization/coursier/commit/727cf73638e49bbe8be76e76c05ebcadc3a4d7f9 on wip/coursierfetch2-work. Diff relative to the reviewed source is one test file, 98 additions and 35 deletions. No product/README/dependency byte changed.
OBSERVED: Final registered run checks out that full candidate SHA in six isolated GitHub jobs: three ordinary ownership-suite runs (9 rows), one malformed-config ownership run (3 rows), unchanged FileCacheTests neighbors once (6 rows), and previous-product-only control (3 rows). Exact original Mill command is retained. No extra verification batch is planned after this native gate. The control must reproduce the two independent-location assertion failures; the same-file guard's expected preservation tests the brief contradiction directly.

## FLAKES ENCOUNTERED — known fixture configuration defect
OBSERVED: Baseline run 38995746243 returns java.lang.Exception with ConfigDb$ConfigDbFormatError in all three cases before their download assertions; frames include CacheEnv.credentialsFromConfig, CacheEnv.defaultCredentials, CacheDefaults.credentials and FileCache.apply. It is the deterministic P2 defect assigned to this lane. Complete native messages/traces and test-command exit code are retained at .lane-evidence/coursierfetch2/coursierfetch2-baseline on the evidence branch. No unexpected first-attempt test failure has occurred.

OBSERVED: Final exact-source workflow is https://github.com/CodexCoder21Organization/coursier/actions/runs/38996063010 . A tracked bounded monitor reads raw Actions state on a two-minute cadence and downloads native artifacts on every terminal conclusion. It does not monitor or alter PR CI. The watch-build skill has no raw Actions run mode, so its documented raw jobs/state endpoint and bounded terminal-state rules are applied.

## 02:48 UTC compilation correction — no test verdict
OBSERVED: Run https://github.com/CodexCoder21Organization/coursier/actions/runs/38996063010 produces zero native rows in every job, because the generated FileCache.apply methods accept complete prefix overloads rather than arbitrary named defaults. The first fixture constructors report `overloaded method apply ... cannot be applied to (...)`; the whole compile log is retained in coursierfetch2-compile-no-verdict. This is my fixture-code error, not a test failure or gate success.
OBSERVED: Corrected all six calls to supply every constructor argument, including the literal booleans, optional factories, constant buffer size, empty loader list and ordinary clock. No assertion, time bound, scenario ordering or product byte changed. This correction will receive a new exact-source gate; no existing positive gate has passed yet.

## 02:49 UTC corrected exact-source selection
OBSERVED: Corrected source is https://github.com/CodexCoder21Organization/coursier/commit/5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3 . Every FileCache.apply call now supplies all 21 parameters, matching the available generated full-argument overload. The only fixture difference after the no-verdict compile is supplying the omitted constant/default fields; all test assertions and operation bounds are unchanged. Full current delta relative to the reviewed source is one test file, 157 additions and 35 deletions.
OBSERVED: The corrected exact-source gate is https://github.com/CodexCoder21Organization/coursier/actions/runs/38996343860 with the same pre-registered six jobs and expected native counts. Compile-only evidence is durable at https://github.com/CodexCoder21Organization/coursier/commit/25dfda62928f40bd63977dbfcc46319a74ccb39b ; no failed compilation is counted as a baseline assertion or a positive.

## 02:53 UTC final native gate and control refutation
OBSERVED: Exact candidate https://github.com/CodexCoder21Organization/coursier/commit/5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3 passes 18/18 native first attempts in https://github.com/CodexCoder21Organization/coursier/actions/runs/38996343860 : three ordinary ownership runs 3/3 each (9); malformed-config suite 3/3; unchanged FileCacheTests neighbors 6/6. Every XML has zero skipped and zero earlier-attempt/history elements. Every checkout SHA and four selected file hashes matches the exact candidate; the control's two product hashes instead match b051ebea. No further test batch will be submitted.
OBSERVED: Config control is red 0/3 at reviewed source -> green 3/3 at corrected source. The same-file waiter guard is green in each of three ordinary runs (3/3) and in the malformed-config run (1/1). Its callback fires with watching=true while both futures remain pending and GET count is exactly one, before owner release.
OBSERVED: Previous URL-based product reproduces both independent-case assertions `assert(secondEntered.await(30, TimeUnit.SECONDS))`, each with count 1. The strengthened same-file guard passes 1/1 at that same old product, including all new alias/waiter assertions. Thus the requested same-file assertion-red old-product result is REFUTED, with a native result and a direct mechanism: equal URLs select the same old coordinator, which already calls this waiter branch. A deliberately failing extra assertion about internal coordinator selection would test a different contract and was not added.
OBSERVED: The proposed previous-product identifier 91c1001b~1 is independently refuted by source/history: it is 519bb1d0, the product-fix commit. The correct previous URL-based product used here is b051ebea6dcadacf3f781687c1a97e89f4eb69f9.

## Per-file change summary
OBSERVED: git diff --stat 91c1001b...HEAD: FileCacheLocationOwnershipTests.scala only, 157 additions and 35 deletions. It supplies complete constructor inputs and requires a real same-file wait through an owned symlink before releasing the HTTP owner. No product, README, dependency, other test or bound is changed in round 2.

## Brief item audit
- DONE: Six constructors supply credentials=Nil and explicit settings before default evaluation; native malformed-config baseline 0/3 -> fixed 3/3.
- DONE: Real symbolic-link alias, lexical/canonical destination assertions, watching=true gate and unchanged byte/count assertions; ordinary guard 3/3 first attempts.
- REFUTED: Same-file guard must be red on old URL-based product; it is green 1/1 because equal URLs already join. Two independent cases are red 2/2 under the same product-only reversal.
- REFUTED: 91c1001b~1 names pre-fix product; source/history show that parent contains the fix. Corrected control is verified against real pre-fix main b051ebea.
- DONE: Retained module command, native XML and exact-source/hash audit; ordinary suites 9/9, config 3/3, neighbors 6/6.
- IN PROGRESS: Update existing draft PR and its description; preserve all evidence remotely and remove workflow branch/processes.

## FLAKES ENCOUNTERED — previous-product control
OBSERVED: The control in run 38996343860 has the two intended independent-location assertion failures at secondEntered.await with latch count 1. These reproduce the known product defect fixed in round 1, with only product files reversed. Native errors/traces and run/source proofs are retained in coursierfetch2-final/coursierfetch2-gate-control; no unexpected first-attempt failure occurred.

## Final state — 2026-10-17 02:55 UTC
OBSERVED: https://github.com/CodexCoder21Organization/coursier/pull/5 is OPEN and remains DRAFT at exact tested head 5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3. Source was fast-forwarded only after the exact-source gate, and body was updated using the required PATCH API and verified by complete readback. The original why-first opening, exact campaign URL and required footer remain. Main still equals the initial b051ebea revision; no second rebase was needed. No PR CI watcher was started; the orchestrator owns that wait.
OBSERVED: The temporary wip/gha-run/coursierfetch2 branch is deleted. All three tracked monitors have exited, and ps -eo pid,ppid,args -ww finds no remaining lane monitor. No local JVM/build slot was used. The checkout is clean; no workflow, log, evidence file, build output or investigation file is in the PR. Only the assigned fixture test file changed in round 2. No merge, enqueue, readiness change, publication, deployment, production-host operation, model delegation, remote test submission, handoff operation or challenge-service call occurred.
OBSERVED: Both P2 test findings are addressed with native evidence. The strict requested output condition that the same-file guard be red on the old equal-URL product is refuted, so no such red result is claimed. This is the only reason the lane reports NOT READY against the literal requested final gate; the 18/18 positive gate and two independent old-product failures are complete. The orchestrator can accept the same-file case as the opposite guard or redefine the requested discriminator; this lane has not invented a different public contract to manufacture that result.

## Final status report
Original request: close the two P2 test findings on the download-location PR, prove configuration independence and real same-file overlap, preserve a draft, and stop at the updated PR. Implementation status: DONE for both findings; requested same-file red criterion: REFUTED. No delegated work is active or recently finished. Landing sweep: all assigned native gates are terminal, the exact tested source and PR description are pushed, and only the orchestrator's review/PR CI decision remains. No handoff action is authorized.
The constructors now receive explicit inputs before any user default is evaluated. The real symlink alias is held in a same-file wait until the public watching callback occurs. Product behavior is unchanged by this round, and the expected old-product independent failures remain observable.
Lessons: (1) Equal-URL same-file joining is an opposite guard for URL-versus-destination ownership, not a negative discriminator. (2) Fluent setters do not prevent earlier default-argument evaluation; fixtures must control constructor inputs. (3) A workflow conclusion cannot replace native test rows: compilation-only runs have no test verdict.

NOT READY: requested same-file red control is refuted (old URL-based product passes 1/1); both P2 changes are at 5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3 in https://github.com/CodexCoder21Organization/coursier/pull/5 ; hermetic-config 0/3 -> 3/3 ; same-file guard fixed product 3/3 ; independent old-product reds 2/2 ; gate https://github.com/CodexCoder21Organization/coursier/actions/runs/38996343860 first-attempt 18/18.
