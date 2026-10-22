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
2026-10-22 22:17 UTC — OBSERVED: Fresh clone at FCOURS2-coursier-20261020 is based on origin/main f7ac8c67dbe757d9129cfa453a37f2867bd1272d; branch fix/FCOURS2-download-length-integrity rebase was a no-op. OBSERVED: https://github.com/CodexCoder21Organization/coursier/pull/6 merged at that commit; its buffer cap does not bound each remaining read or reject early EOF. OBSERVED: RCOURS evidence names findings.md rather than the brief's RCOURS-findings.md; the full findings and test sources have been recovered. OBSERVED: The repository uses Mill 0.12.15 and utest 0.9.1 rather than kompile; no scripts/test.bash exists. OBSERVED: No Addendum heading is present in full-FCOURS2.md. INFER: The framing defects remain applicable, including CacheUrl silently starting over on wrong range starts; next checkpoint is a single-attempt failing register using public FileCache with zero download retries.
