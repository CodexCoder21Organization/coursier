# FCOURS2 download length integrity

## Plan
- [x] Fresh clone; branch from current main; compare merged response-buffer change and read prior evidence.
- [ ] Import real-loopback reproductions and add applicable gap cases; measure single-attempt baseline.
- [ ] Fix framing checks, bounded copy and temporary-file cleanup; confirm all new cases pass 30/30.
- [ ] Run existing cache-module tests, review tests and code, rebase, push product branch and open PR.
- [ ] Audit attempts, scan/push evidence and verify remote SHAs; verify process cleanup.

## Reading
- Repository README.md and DEVELOP.md; no AGENTS.md exists.
- DocumentationRepository PHILOSOPHY.md and architecture/TESTING.md (reading in progress).
- RCOURS evidence at b8928faa: findings.md (the requested RCOURS-findings.md is actually named findings.md), RcoursResponseTests.scala, RcoursPausedResponseTests.scala, RcoursFragmentedLengthTests.scala, utest-retry-audit.md and run-command.py.

## Progress
2026-10-22 22:14 UTC — OBSERVED: Fresh clone at FCOURS2-coursier-20261020 is based on origin/main f7ac8c67dbe757d9129cfa453a37f2867bd1272d; branch fix/FCOURS2-download-length-integrity rebase was a no-op. OBSERVED: https://github.com/CodexCoder21Organization/coursier/pull/6 merged at that commit; its buffer cap does not bound each remaining read or reject early EOF. OBSERVED: RCOURS evidence names findings.md rather than the brief's RCOURS-findings.md; the full findings and test sources have been recovered. OBSERVED: The repository uses Mill 0.12.15 and utest 0.9.1 rather than kompile; no scripts/test.bash exists. OBSERVED: No Addendum heading is present in full-FCOURS2.md. INFER: The framing defects remain applicable, including CacheUrl silently starting over on wrong range starts; next checkpoint is a single-attempt failing register using public FileCache with zero download retries.

2026-10-22 22:15 UTC — OBSERVED: Initial evidence checkpoint local and remote SHAs both 77e5823504c2ace3b29354f775f97fc788438899; scan found no matches. OBSERVED: Complete PHILOSOPHY.md and TESTING.md, all three prior real-server test sources, and the repository source paths have now been read. OBSERVED: CsTests selects utest.runner.Framework; plain TestSuite has no retry mixin, matching RCOURS's audited executor. OBSERVED: git identity was unset, so repository-local identity was configured to the authenticated GitHub account's no-reply address before the evidence commit. INFER: The mechanism to fix is unbounded bulk reads plus unvalidated EOF, followed by unconditional rename; the reproducer sends a fixed short body or releases the final fragment only after public progress confirms the first fragment was consumed. INFER: A fixed-length input must bypass BufferedInputStream prefetch and limit every underlying read to the remaining count, then reject early EOF before rename. INFER: The next checkpoint is a failing test register, currently being drafted by gpt-6-luna; no product edit has been made.

## Resource invariants before implementation
| Resource | Creator / owner / closer | Terminal state / public observation | Public-path test |
| --- | --- | --- | --- |
| Network response stream | ConnectionBuilder creates; Downloader owns; closes in finally and disconnects connection | Length exhaustion is successful only at the exact count; early EOF is failure, with URL and expected/actual bytes | Immediate EOF, held EOF, fragmented trailing body, exact and zero lengths |
| Temporary cache file | Downloader owns under per-file lock; output closes before validation outcome; invalid response deletes it before unlocking | Never renamed after framing failure; a later caller cannot resume a rejected prefix | Assert temp and final absent after each rejection; retry by a new caller gets a full un-ranged response |
| Published cache entry | Downloader atomically renames only after complete body and valid metadata | Success exposes exact framed bytes; failure never exposes a final file | FileCache result and filesystem assertions, including pending progress callback |
| Persisted prefix | Existing temp is input to Range request; Downloader appends only on validated 206 | 200 replaces prefix; wrong/missing/incomplete Content-Range fails without a silent request | Prefix seeded before real request, valid206, wrong start/end/total, ignored Range200 |
| Callbacks | CacheLogger receives progress and terminal success flag | Progress cannot exceed advertised complete length; callbacks can observe no final entry before successful completion | Held EOF and fragmented over-read use public callback latches; terminal failure flag asserted |

No existing test or assertion has been edited. Branch work is limited to new tests until the baseline run.

2026-10-22T22:16:06.756490+00:00 — OBSERVED: No Addendum heading is present at this checkpoint. OBSERVED: The fresh recursive clone completed and submodules now match main; git status is clean before new test source arrives. OBSERVED: The baseline cache-test compilation is running locally with Mill --no-server -j2, zero compiler errors so far; starting cgroup oom_kill is2. OBSERVED: The test author is writing only the new integrity suite, with no permission to run builds or change existing tests. INFER: A206 with a matching start but an end/total inconsistent with an open-ended artifact request must also be rejected; a200 must replace rather than append to the saved prefix. INFER: Next checkpoint remains the exact single-attempt baseline, followed by the framing fix.

2026-10-22T22:16:32.238391+00:00 — OBSERVED: Baseline cache.jvm[2.13.16].test.compile completed successfully on the first invocation (52.07s, zero compiler errors). OBSERVED: Evidence checkpoint local and remote SHAs both9962df6e0169fc28e9db0539c21358e71b457be6. OBSERVED: The current product working tree remains clean; gpt-6-luna is writing the new test source only. INFER: Main's merged buffer sizing is buildable but does not implement the framing contract; next checkpoint is the failing real-server register.

2026-10-22T22:17:41.633056+00:00 — OBSERVED: The core3 new public FileCache scenarios fail3/3 on main f7ac8c67, one test attempt each, no skipped/errors/retry mixins; expected DownloadError assertions fail because each call returns success for an incomplete response. OBSERVED: These are a9-byte declaration with6bytes+EOF, a9-byte declaration with0bytes+EOF, and6bytes held open until callback inspection thenEOF. OBSERVED: Baseline XML, source and log are saved; the product source remains unedited. OBSERVED: Prior evidence push local/remote bothd8241aebc99cb22d231a21f244c137a5c061aa08. INFER: The early-EOF mechanism is confirmed independently on the now-merged buffer-sizing base; next checkpoint is fragmented over-read and the complete gap register before any fix.
