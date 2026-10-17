# coursierfetch2 findings
Started 2026-10-17 approximately 02:33 UTC; deadline 03:18 UTC.

## Plan
- DONE: Read prior findings, required Testing Architecture and Philosophy sections, fork README; fresh clone, identity and initial fetch/rebase.
- IN PROGRESS: Verify malformed-config baseline at 91c1001b; harden six constructors and the real same-file waiter guard.
- PENDING: Run native three-run gate, config check, previous-product control and neighbors; audit first attempts.
- PENDING: Checkpoint evidence, review test changes, update draft PR body and finish process audit.

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
