# revcoursier3 independent review

Target: https://github.com/CodexCoder21Organization/coursier/pull/5 at d5eec8d10bf59305b4263bb96e741e45408c9f85.

## Plan
- IN PROGRESS: Read required documents and earlier findings; clone the exact review head.
- PENDING: Check configured retry forwarding and enumerate Downloader construction sites.
- PENDING: Compare ownership test expressions mechanically and verify destination-lock implementation is unchanged from 91c1001b.
- PENDING: Review retry test isolation, bounds, and earlier-head control; run the module command once.
- PENDING: Push evidence only to wip/revcoursier3-evidence and report counts.

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
