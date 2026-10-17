A BuildTestRunner gate on identical source had one machine stop in dependency preparation while sibling jobs finished in about 46 seconds: https://github.com/CodexCoder21Organization/BuildTestRunner/actions/runs/38988677881 . Expanded worker capture reproduced the first-attempt failure and located the wait in Coursier's URL-lock polling: https://github.com/CodexCoder21Organization/BuildTestRunner/actions/runs/38991888268 . This is part of the BuildTest utilization / projections campaign tracked in handoff url://handoff/handoffs/hf-2026-08-24-fix-startup-compaction-delta-heap-exhaustion-then-revalidate-the-extreme-suite .

Downloads with the same URL shared one JVM-wide owner even when their cache directories differed. A waiting cache inspected only its own file, which the other cache never writes, then entered growing polling delays. Download ownership now follows the canonical destination path, so independent cache locations proceed separately and aliases of one destination still share one owner. Public URL-lock behavior, file-lock handling, retry settings, time limits, dependency versions and release versions are unchanged.

Public FileCache tests hold one real loopback HTTP response and require another cache to return its complete bytes before that response is released. They also cover a held owner that returns HTTP 500 and two callers sharing one canonical cache location. Fixture HEAD requests and GET bodies are handled separately, with explicit cache settings and owned cleanup.

Validation on exact head `91c1001b4191da7c0842f41d559dab5f2afb2c5f`:

- Assertion-red first: both affected cases failed at the held-owner GET-entry assertion on unchanged product; the same-location guard passed. [Native baseline run](https://github.com/CodexCoder21Organization/coursier/actions/runs/38993111192).
- Final first attempts: **15/15** — all three ownership cases each **3/3**, plus all **6/6** unchanged FileCacheTests neighbors. No skipped or earlier attempts. [Final gate](https://github.com/CodexCoder21Organization/coursier/actions/runs/38993509459).
- Product-only reversal: **2/2** intended assertion failures, with the same-location guard still passing, in that final run. Only CacheLocks.scala and Downloader.scala were restored; test, README and product hashes were audited.
- Existing public timeout tests also passed **2/2** first attempts, establishing that the original symptom was not a missing generic silent-network backstop. [Resolver bounds run](https://github.com/CodexCoder21Organization/kompile-core/actions/runs/38992060180).

The owning project uses Mill/uTest. Gates used its real `cache.jvm[2.13.16].test.testOnly` command on isolated GitHub machines and audited native XML and exact source hashes. [Complete investigation and native evidence](https://github.com/CodexCoder21Organization/coursier/tree/wip/coursierfetch1-evidence).

Existing-test edits: none. The change adds one test suite; it changes no existing assertion, test bound or iteration count. The workflow, investigation notes and native evidence live only on separate wip branches.

The brief stops at this upstream fix PR. No artifact was published and no downstream version was changed.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
