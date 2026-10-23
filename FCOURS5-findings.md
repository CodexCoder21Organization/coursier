# FCOURS5 findings

PR: https://github.com/CodexCoder21Organization/coursier/pull/7

## Plan
- [completed] Read required sources, clone fresh, rebase PR onto current main and check overlap.
- [completed] Add A1 loopback tests, record single-attempt failure on ac74fa03, checkpoint evidence.
- [in progress] Filter trimmed empty list members, run each new case 30 times and targeted register once, review tests and implementation.
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

2026-10-23 04:01:09 UTC — OBSERVED: Initial evidence checkpoint verified local=remote=cd82de9799bb3f262b14d6424d270b67f4843153 after the required scan. OBSERVED: Recursive clone and submodule update completed; added seven A1 tests in the existing suite, preserving all other assertions, locally committed at 261ad06fd. OBSERVED: Single-attempt before test command started on ac74fa03 product code via Mill --no-server -j1 and plain utest; test-only commit changes no production source. OBSERVED: No Addendum headings. INFER: Next checkpoint is two acceptance failures and unknown-name rejection result, then the one-line parser correction.

2026-10-23 04:05:54 UTC — OBSERVED: before.xml contains exactly three unique test cases: both requested acceptance cases FAIL at complete-body assertion DownloadResponseFramingTests.scala:340 and the full-message bogus rejection PASS; plain utest has one qualified event per case and no retry wrapper. OBSERVED: Product main-source diff from ac74fa03 is empty. OBSERVED: Independent reviewer confirms the planned parser filter and identifies encoded resume with empty members as the remaining A1 boundary; added an eighth test. INFER: Next checkpoint is boundary baseline and evidence push before the filter.

## A1 public behavior invariant register
| Value/owner | Required behavior | Public proof |
|---|---|---|
| Parsed coding list; downloader owns normalization | Trimmed empty members have no effect; unknown nonempty members still reject with complete original field | Empty field, comma-only, identity list and bogus rejection |
| Response/body and temporary cache bytes; downloader closes streams and owns publication | Decoding, length validation, saved-prefix handling and cleanup remain unchanged after empty-member removal | Compressed list, cut-off identity list, identity resume and encoded-resume rejection |
| Final artifact; published by successful download owner | Exact complete body visible; no temporary entry after success or error | Existing public FileCache observation helpers for every new case |

2026-10-23 04:06:46 UTC — OBSERVED: Four additional A1 boundary cases fail on baseline at their full-byte/full-message assertions. OBSERVED: The newly authored encoded-resume setup initially used a three-byte Content-Range with a longer compressed wire body; corrected the setup to use the exact compressed wire length, matching the existing encoded-resume case. OBSERVED: Its full expected-message assertion is unchanged; a fresh single-attempt baseline run of that corrected case is in progress and the earlier setup failure is preserved in before-boundaries.xml. INFER: Next checkpoint is its proper unsupported-encoding failure followed by the parser fix; the setup run is additional development evidence, not a retry of a gate.

2026-10-23 04:07:58 UTC — OBSERVED: Corrected encoded-resume baseline fails once at DownloadResponseFramingTests.scala:356 because it receives Unsupported Content-Encoding rather than the complete encoded-resume rejection message; no range/setup error or timeout occurs. OBSERVED: Evidence checkpoint 2aae0db1de6ca6cbd4e9d53000020bba9257e2b2 verified local=remote. OBSERVED: Added the single post-trim .filter(_.nonEmpty) production line and launched the sequential 30-repeat plus 103-case register gate under the memory-slot wrapper. OBSERVED: No Addendum headings. INFER: The gate may wait on the other lane holding the memory slot; next checkpoint is first green run then repeat totals.

2026-10-23 04:09:38 UTC — OBSERVED: Final independent test-completeness and code-review verdicts are PASS after adding the encoded-resume boundary; final local diff touches only Downloader.scala and DownloadResponseFramingTests.scala. OBSERVED: Product commit is 6fa47c3435700763a1d2fc82dd7399d0012184a6 and main is unchanged. OBSERVED: Gate wrapper is waiting for the memory slot held by F188d, with its three-hour acquisition deadline and EXIT release trap; no test is running in this lane during that wait. OBSERVED: README already documents supported and unsupported codings and requires no change for this parser correction. INFER: Next checkpoint is first green eight-case run after lock acquisition.
