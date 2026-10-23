# FCOURS5 findings

PR: https://github.com/CodexCoder21Organization/coursier/pull/7

## Plan
- [completed] Read required sources, clone fresh, rebase PR onto current main and check overlap.
- [in progress] Add A1 loopback tests, record single-attempt failure on ac74fa03, checkpoint evidence.
- [pending] Filter trimmed empty list members, run each new case 30 times and targeted register once, review tests and implementation.
- [pending] Rebase, push PR branch, update description, scan/push evidence, verify remote SHAs and process cleanup.

## Scope
Only A1 and related B gaps. A2–A5 belong to the separate follow-up. No merge, queue, publish, remote run, handoff command, timeout increase or assertion weakening.

## Progress
2026-10-23 03:56:29 UTC — OBSERVED: Read full-FCOURS5.md and RCOURS3-findings.md; no Addendum headings at startup. OBSERVED: Repository search found CodexCoder21Organization/coursier. OBSERVED: Required documentation downloaded through GitHub API and fresh recursive clone started. INFER: Next checkpoint is fresh main comparison and failing A1 tests.

2026-10-23 03:58:04 UTC — OBSERVED: Fresh branch rebase is unchanged at ac74fa03ab018e66a67f293dbae7a4fe3a7bd367; origin/main is still f7ac8c67dbe757d9129cfa453a37f2867bd1272d. OBSERVED: Read README.md and DEVELOP.md; no AGENTS.md; this repository uses Mill/Scala and plain utest, not kompile. OBSERVED: Read PHILOSOPHY.md and TESTING.md and the RCOURS3 remote test source/runner. OBSERVED: No Addendum headings. INFER: Main overlaps the buffer prerequisite only and does not supersede response validation; next checkpoint is failing A1 cases.

## Mechanism and test plan
The mechanism I am about to fix is that split with a negative limit retains empty encoding members, and validation treats them as unsupported coding names; the reproducer is a real loopback server sending an empty field or comma-empty identity list through FileCache. Filter after trimming before validation and decoder selection; keep the original full header in error messages. Public tests verify complete bytes, final publication and temporary cleanup for accepted responses, and the complete message plus no cache bytes for unsupported responses.

## Existing test edit required by A1
DownloadResponseFramingTests: rename `unknown and empty encodings cannot publish response bytes` to `unknown encodings cannot publish response bytes`; its values change from custom/br/gzip-custom/empty to custom/br/gzip-custom. The empty value asserts the wrong contract and moves into its own positive regression test, with full byte/publication/cleanup checks. Unknown-name assertions remain identical. This explicit contract correction is requested by A1 and RCOURS3, not an assertion loosened to hide a failure.
