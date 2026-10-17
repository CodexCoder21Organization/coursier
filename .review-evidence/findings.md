# revcoursier3 independent review

Target: https://github.com/CodexCoder21Organization/coursier/pull/5 at d5eec8d10bf59305b4263bb96e741e45408c9f85.

## Plan
- DONE: Read required documents and earlier findings; clone the exact review head.
- DONE: Check configured retry forwarding and enumerate Downloader construction sites.
- DONE: Compare ownership test expressions mechanically and verify destination-lock implementation is unchanged from 91c1001b.
- DONE: Review retry test isolation, bounds, and earlier-head control; run the module command once.
- DONE: Push evidence only to wip/revcoursier3-evidence and report counts.

OBSERVED: Review started 2026-10-17; disk has 1.4 TB available. No product edits or PR edits are authorized for this review.

## FLAKES ENCOUNTERED
None observed yet.

## Challenges for the orchestrator
None observed yet.

## Source and native evidence milestone — 03:30 UTC
OBSERVED: Fresh clone at exact pinned head remains unchanged after the initial fetch/rebase. Root README read; no AGENTS.md exists. Required named Testing Architecture and Philosophy sections and both earlier findings read. github-repos lookup locates the expected repository.
OBSERVED: FileCache.download is the sole production Downloader construction site in modules/cache and forwards retryCount=retry, retryBackoffInitialDelay=retryBackoffInitialDelay, retryBackoffMultiplier=retryBackoffMultiplier. These resolve to instance fields, not CacheDefaults; the target implementation consumes precisely those fields in Retry. No product edits were made.
OBSERVED: Mechanical token audit compares 5470aee6 with the pinned head: all 31 assertion expressions, six FileCache constructor argument lists and six Await.result calls are equal. The whole token stream differs only by twelve removed brace tokens, consisting of redundant lambda and try-body wrapper pairs in the three HTTP handlers. Each lambda still has a single try/finally whose body is the same single if/else expression. No behavior change is present. Whitespace-only equality of the entire file is not literally true, but the claimed expression equality is true.
OBSERVED: CacheLocks.scala and Downloader.scala are byte-identical to both 91c1001b and 5470aee6. Destination coordination is unchanged.
OBSERVED: Independently downloaded native artifacts from https://github.com/CodexCoder21Organization/coursier/actions/runs/38998189119 and audited all XML rows, process exits, source proofs and six hashes per job. Positive rows are 35/35 first attempts, zero skipped/history. FileCache-only reversal is 0/1 with the intended waiterEntered.await assertion failure; its FileCache hash matches 5470aee6 and every other recorded source hash matches the final head. This verifies the new regression fails for the missing forwarding, with the final destination implementation and test retained.
OBSERVED: Single local module command runs under one build slot, bounded at 15 minutes: ./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests coursier.cache.FileCacheRetrySettingsTests. Compilation is in progress; no pass is claimed without XML. Checkout-local cs link supplies the name expected by the unchanged build and targets the working coursier executable.

## Retry test review
OBSERVED: New suite uses FileCache.file and its public logger callback only. It starts its own 127.0.0.1 port-zero HTTP server, owns the temporary directory, and passes every FileCache constructor setting explicitly, including Nil credentials and owned executor. Its second instance is derived from that explicit instance with only its logger changed. It reads no user configuration and changes no global property.
OBSERVED: Owner GET entry occurs while the real download owns the destination. The peer then emits three watching callbacks while the owner remains held. Those callbacks require two Retry loop delays; they are not a signal sent before actual waiting. No fixture sleep is used for ordering. Owner and peer must remain incomplete, there must be one GET, and after release both must return the same canonical destination with exact bytes [1,2,3] and still one GET.
OBSERVED: Each outer event/result wait is bounded at 30 seconds. The server's held response is released in finally on all assertion outcomes; server and owned pools are stopped, pool termination has a 10-second bound, and the owned tree is removed. The local whole command also had a 15-minute deadline. There is no additional retained resource or shared fixture introduced.
OBSERVED: The retained final mutation job uses only the one-minute test-JVM delay property and the old FileCache implementation. Its assertion fails after the first watching event because the omitted active delay argument makes the next poll wait one minute. Product fields forwarded at the final head restore the explicit one-millisecond setting and multiplier 1.0, so three callbacks complete. Separate multiplier control provides a one-millisecond ambient initial delay and multiplier 10000000; the explicit multiplier still governs and the native row passes.
INFER: The old-product discriminator is the isolated supported-property control, not the ordinary default-settings run. The same-file waiting branch deliberately allows more None results than retry count, as Retry.retryOpt documents; it does not claim to measure exception retry count. The count forwarding is verified directly at the sole production construction site. These are explicit limits of the evidence, not additional findings.

## Single local module run — 03:29 UTC
OBSERVED: Exact pinned source ran once, exit zero, native XML 4/4 first attempts, zero failure/error/skipped/history. Ownership rows: independent output 0.323 s, independent owner error 0.033 s, same-file alias 0.071 s. Retry row: explicit settings same-file waiting 0.115 s. Native report, invocation, complete command log and audit are retained. Local build slot was released by the command's EXIT trap. No additional verification batch was run.
OBSERVED: Initial evidence milestone pushed to https://github.com/CodexCoder21Organization/coursier/tree/wip/revcoursier3-evidence/.review-evidence . Native gate URL is https://github.com/CodexCoder21Organization/coursier/actions/runs/38998189119 . Source, tests and README remain identical to the pinned head; the evidence branch adds only .review-evidence.

## Changed-file review
OBSERVED: Round 4 git diff --stat against 5470aee6 is four files, 215 additions and 86 deletions.
- README.md states instance retry settings take precedence over JVM retry properties.
- FileCache.scala forwards three matching active settings from the configured instance.
- FileCacheLocationOwnershipTests.scala has formatting and redundant-wrapper removal only, with every asserted expression, constructor list and Await.result unchanged.
- FileCacheRetrySettingsTests.scala adds the owned public same-file waiting regression described above.
OBSERVED: This review itself changes no source, test, README, build definition or pull request.

## Limits
OBSERVED: Runtime evidence is Linux only. The prior review's Windows symbolic-link prerequisite and case-insensitive filesystem limit remain unverified; neither is a newly demonstrated defect. No new platform claim is made. The orchestrator owns PR CI/full-suite waiting.

## Completion audit
- DONE: Earlier findings, required documentation, exact source, root README and repository instructions checked.
- DONE: Three instance settings and sole Downloader construction verified.
- DONE: All 31 assertions, six constructor lists, six result waits mechanically matched; redundant wrappers reviewed; no semantic change found.
- DONE: New retry test isolation, ordering, bounds, old-source assertion failure and multiplier control checked against native artifacts.
- DONE: Destination-lock source byte equality with approved 91c1001b verified.
- DONE: Module command run exactly once; native 4/4 first attempts.
- DONE: Final evidence checkpoint, cleanup and process audit.

## Final review result
OBSERVED: P1 0, P2 0. Prior P2 about settings forwarding is addressed. No newly observed unrelated first-attempt failure; the retained old-source failure is the assigned control.

## Final cleanup receipt
OBSERVED: Single module command and artifact download have exited. Temporary build output and local launcher directory removed after confirming zero tracked files beneath them. Process audit via ps -eo reports zero remaining lane-owned Java or deadline processes; the acquired slot was released. No remote test, PR CI watcher, production action, PR edit, merge or model delegation occurred. The only local invocation workaround was the cs name link required by the unchanged build, now removed.

REVIEW d5eec8d10bf59305b4263bb96e741e45408c9f85 CLEAN
