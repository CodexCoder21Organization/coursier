A BuildTestRunner gate on identical source had one machine stop in dependency preparation while sibling jobs finished in about 46 seconds: https://github.com/CodexCoder21Organization/BuildTestRunner/actions/runs/38988677881 . Expanded worker capture reproduced the first-attempt failure and located the wait in Coursier's URL-lock polling: https://github.com/CodexCoder21Organization/BuildTestRunner/actions/runs/38991888268 . This is part of the BuildTest utilization / projections campaign tracked in handoff url://handoff/handoffs/hf-2026-08-24-fix-startup-compaction-delta-heap-exhaustion-then-revalidate-the-extreme-suite .

Downloads with the same URL shared one JVM-wide owner even when their cache directories differed. A waiting cache inspected only its own file, which the other cache never writes, then entered growing polling delays. Download ownership now follows the canonical destination path, so independent cache locations proceed separately and aliases of one destination still share one owner. Public URL-lock behavior, file-lock handling, retry settings, time limits, dependency versions and release versions are unchanged.

Public FileCache tests hold one real loopback HTTP response and require another cache to return its complete bytes before that response is released. They also cover a held owner that returns HTTP 500 and two callers sharing one canonical cache location. Fixture HEAD requests and GET bodies are handled separately, with explicit cache settings and owned cleanup.

Validation on exact head `91c1001b4191da7c0842f41d559dab5f2afb2c5f`:

- Assertion-red first: both affected cases failed at the held-owner GET-entry assertion on unchanged product; the same-location guard passed. [Native baseline run](https://github.com/CodexCoder21Organization/coursier/actions/runs/38993111192).
- Final first attempts: **15/15** — all three ownership cases each **3/3**, plus all **6/6** unchanged FileCacheTests neighbors. No skipped or earlier attempts. [Final gate](https://github.com/CodexCoder21Organization/coursier/actions/runs/38993509459).
- Product-only reversal: **2/2** intended assertion failures, with the same-location guard still passing, in that final run. Only CacheLocks.scala and Downloader.scala were restored; test, README and product hashes were audited.
- Existing public timeout tests also passed **2/2** first attempts, establishing that the original symptom was not a missing generic silent-network backstop. [Resolver bounds run](https://github.com/CodexCoder21Organization/kompile-core/actions/runs/38992060180).

The owning project uses Mill/uTest. Gates used its real `cache.jvm[2.13.16].test.testOnly` command on isolated GitHub machines and audited native XML and exact source hashes. [Complete investigation and native evidence](https://github.com/CodexCoder21Organization/coursier/tree/wip/coursierfetch1-evidence).

No main-branch test is edited. The change adds one test suite, with the round-2 changes to that suite listed below; no prior assertion, test bound or iteration count is weakened. The workflow, investigation notes and native evidence live only on separate wip branches.

The brief stops at this upstream fix PR. No artifact was published and no downstream version was changed.

### Round 2: fixture configuration and same-file overlap

Review found that the new fixtures read unrelated user credentials configuration during construction, and that the same-file guard could release its owner before the second caller reached the waiter path. This round addresses those two test findings without changing product behavior.

All six constructors now supply all 21 arguments, including `credentials = Nil` and their policy, checksum, executor, TTL, redirection and retry settings directly. Constant fields are explicit because the generated `apply` overloads require a complete argument list. The shared-location case uses an owned symbolic-link directory, asserts distinct path spellings with equal canonical destinations, and holds the first HTTP response until the second caller emits `downloadLength(watching = true)` from the same-file wait. Both callers must remain pending with one GET before owner release; the existing exact-byte and one-GET assertions remain.

Validation at `5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3`:
- Malformed-config baseline at the reviewed head: 0/3 native successes; all three fail with `ConfigDb$ConfigDbFormatError`. [Baseline run](https://github.com/CodexCoder21Organization/coursier/actions/runs/38995746243).
- Exact-source positive gate: **18/18 native first attempts**, with all three ownership cases each 3/3 (9), malformed-config run 3/3 and unchanged neighbors 6/6. No skipped or earlier-attempt rows. [Final gate](https://github.com/CodexCoder21Organization/coursier/actions/runs/38996343860).
- Product-only reversal: **2/2 intended independent-location assertion failures**; the strengthened same-file guard remains **1/1 successful** on the old URL-based product. The requested same-file red control is refuted by this result, because equal URLs already share the old coordinator. The fixed product preserves that guard 3/3. Source and test file hashes were checked in every job against the full candidate SHA.
- One earlier candidate did not compile and produced no native test rows; it is excluded from every gate count. Complete native evidence, compile-only evidence and findings are on [the separate evidence branch](https://github.com/CodexCoder21Organization/coursier/tree/wip/coursierfetch2-evidence/.lane-evidence/coursierfetch2).

Control clarification: `91c1001b~1` already contains the destination coordinator. The previous-product control restores only the two product files from `b051ebea6dcadacf3f781687c1a97e89f4eb69f9`. The strengthened same-file guard uses equal URLs, so both URL-based and destination-based ownership should preserve it. That guard cannot honestly be an assertion-red discriminator between those products; the two independent-location cases provide the negative control.

Existing-test edit ledger for this round:
- All three `FileCacheLocationOwnershipTests` scenarios: replace six constructors plus chained setters with equivalent explicit constructor settings and `credentials = Nil`; no existing assertion or bound changes.
- `same location joins its owner`: replace the dot-segment alias with a real symbolic link; replace the pre-lock `downloadingArtifact` signal with `downloadLength(watching = true)`; add lexical/canonical destination, pending-future and pre-release single-GET assertions. Retain all complete-byte, canonical-result, exact-count and cleanup assertions.
- No other test is edited. No timeout or iteration count changes, product edit, publication or deployment.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
