# RCOURS4 final-head review

PR: https://github.com/CodexCoder21Organization/coursier/pull/7
Product branch: fix/FCOURS2-download-length-integrity
Pinned head: 6fa47c3435700763a1d2fc82dd7399d0012184a6
Main: f7ac8c67dbe757d9129cfa453a37f2867bd1272d

## Plan
- [completed] Read full brief, required documentation, fresh clone README and DEVELOP; verify PR, rebase locally and compare current main.
- [completed] Trace final diff, retrieve independent registers, define blank-member boundary probes and audit invariants.
- [completed] Run independent register and blank-member probes with one attempt per case; compare inherited cases with main using identical real-server sources.
- [completed] Review test comprehensiveness and compliance; scan and push evidence at milestones; verify remote SHAs and process cleanup; deliver A/B/C.

## Scope and reading
OBSERVED: Read full-RCOURS4.md; it has no Addendum headings at startup. OBSERVED: README.md and DEVELOP.md describe Mill/Scala, no scripts/test.bash and no root AGENTS.md. OBSERVED: Retrieved PHILOSOPHY.md and architecture/TESTING.md through authenticated GitHub API; copies are retained here. OBSERVED: Read remote RCOURS3 and FCOURS5 findings, test sources and launchers. INFER: Plain utest with Mill is the correct local single-attempt gate; kompile execute/maxTestAttempts does not apply to this repository.

## Progress
2026-10-23 05:01 UTC — OBSERVED: Fresh recursive clone is in RCOURS4-coursier-20261020; local review/RCOURS4-pinned rebased onto origin/main without any change, remains 6fa47c3435700763a1d2fc82dd7399d0012184a6. OBSERVED: PR is OPEN at that exact head, main remains f7ac8c67dbe757d9129cfa453a37f2867bd1272d. INFER: Main overlaps only the buffer-sizing prerequisite and does not supersede the response-validation change. OBSERVED: No PR/source edits, builds, tests, remote runs or handoff commands so far. INFER: Next checkpoint is the independent register and parser-boundary probe source, followed by single-attempt results.

2026-10-23 05:03:33 UTC — OBSERVED: First scanned evidence push verified local=remote=b80a4c6fe434600c85549c5680ebdd067d6b4868. OBSERVED: Local test-only commit adds byte-identical RCOURS3 review and decoder-ending registers; production diff from pinned head is empty. OBSERVED: Single-attempt head gate started with direct Mill 0.12.15 no-server entry and -j1 (one build JVM and one test JVM); memory preflight passed. OBSERVED: Authored 16 independent real-server blank-member boundary cases in evidence, including repeated-field checks which may reproduce inherited A2. INFER: Next checkpoint is exact XML/events for the unchanged independent register, then running the new boundary cases without editing any existing assertion.

2026-10-23 05:05 UTC — OBSERVED: The recovered direct launcher failed before compilation because out/mill-lock parent directory was absent; bootstrap-failure.log and result retained, zero test attempts. OBSERVED: Created only the standard untracked out/ directory; detached initial shell had also ended before launching tests, so the active shell now waits on the recorded child PID. INFER: Neither setup event is a product finding or a test retry; next checkpoint is the first actual independent register result.

## Review invariants and pre-registered probes
| Resource | Create / own / close | Terminal rule and caller behavior | Proof |
|---|---|---|---|
| Encoding list | URLConnection supplies field; downloader normalizes and validates | Remove only empty trimmed members; all nonempty unknown names reject; reverse supported coding order | Original A1 cases, new unknown-first/middle/last probes, final eight A1 tests |
| Response and decoding streams | Download owner opens and finally closes | Actual wire count/framing and all required decoding must complete before promotion | Fixed/chunked/range matrix; RCOURS3 decoder endings |
| Saved temporary prefix | Previous owner persists, next file-lock owner appends/replaces | Blank-only 206 is identity and must satisfy range and framing; full 200 replaces; encoded partial rejects | New fixed/chunked/short/overlong/missing-ending/pending-publication probes |
| Final cache file | Download owner moves only after success | Rejections leave no final or temporary bytes; pending chunk ending cannot publish | Success/error observation helpers and latch probe |
| Repeated encoding fields | Connection header accessor supplies only one value | All field lines should contribute to coding list; blank final field must not hide earlier coding | Two new repeated-field probes, compare identical sources against main |

## Compliance inventory (preliminary)
OBSERVED: Review edits no existing tests or production files; two unchanged independent registers are new local files, and the new blank-member register is evidence only until the first gate finishes. OBSERVED: Against main, PR changes one existing test in DownloadBufferCapacityTests.scala:267, renaming unsupported custom/raw-body case to a real deflate/decoded-body case; all capacity, buffer-observation, body and close checks remain, with encoded wire length replacing raw length. OBSERVED: PR adds a separate empty-body zero-read case in that file. OBSERVED: FCOURS5 changes the PR-added framing suite (not a main test): removes the empty string from the unknown-coding rejection list and adds an explicit success case; the three unknown-name rejection assertions are unchanged. INFER: These are documented contract corrections, not hidden assertion weakening; however the literal C criterion of no edited main tests is not met by the buffer-case edit and must be reported explicitly.

2026-10-23 05:06 UTC — OBSERVED: Scanned probe/invariant checkpoint verified local=remote=ad0ef69cf2627210ede72169e2ec8f64bdfdaad9. OBSERVED: Head register has compiled the Mill definition and begun product compilation, with one observed JVM so far and no test result yet. OBSERVED: Rechecked full-RCOURS4.md and found no Addendum headings. OBSERVED: Added-line audit finds no reflection, Mockito/mocks, skipped-test markers, JVM tuning flags or retry wrappers; exactly one modified main test file is DownloadBufferCapacityTests.scala. INFER: Next checkpoint is first-attempt head XML and per-case event audit, then new probes and identical main comparison.

## Dedicated test-comprehensiveness pass
OBSERVED: The final PR has real-loopback fixed/chunked/close-delimited, encoded/identity, range/full-body, HEAD/no-body/redirect/error and concurrent same-file cases; assertions cover exact bytes/messages, no final or temporary publication on rejection and pending publication before framing ends. OBSERVED: Eight A1 cases add empty-field/blank-members, comma-only, unknown coding, case-insensitive gzip/deflate/stacked decoding, short lengths, saved-prefix completion and encoded-resume rejection. OBSERVED: Test suites extend plain TestSuite, while the saved utest 0.9.1 Executor.utestWrap calls runBody once. INFER: Final A1 coverage corrects the introduced gap, but four inherited coverage groups remain: repeated coding fields; incomplete later gzip member headers; progress within a gzip member; completion of outer decoding trailers after inner EOF. INFER: New repeated-blank probes belong to the first inherited group if main shows the same acceptance. INFER: A final B verdict must distinguish these known inherited gaps from newly introduced behavior and must not require their repair in this unchanged review-only PR.

## Dedicated code/compliance pass
OBSERVED: New errors include the artifact URL and full offending header or expected/actual counts; error tests compare complete messages, and framing/output failures retain their own causes. OBSERVED: The filter operates after trim/lowercase and before unknown-name validation; it cannot erase a nonempty name within a single field. OBSERVED: No test visibility was increased; no timeout/iteration reduction or retry mixin was added; unchanged test bodies outside the inventoried buffer case are untouched. INFER: C is FAIL under the brief's literal no-edited-main-tests criterion, solely because of that documented buffer-case contract change; the other named compliance checks pass. OBSERVED: The A1 empty-field contract correction is in a PR-added test file and therefore is not an edit to a test on main. OBSERVED: Review itself makes zero existing-test edits.

2026-10-23 05:10:58 UTC — OBSERVED: Fresh build has advanced through eleven compilation stages with no error markers and no test result yet; XML/event audit script is ready. OBSERVED: Saved final length/framing/buffer test sources and the main buffer suite for the exact before/after inventory. OBSERVED: RFC 9110 sections 5.2, 5.6.1.2 and 8.4 confirm the list and repeated-field contracts; reading links retained. OBSERVED: No Addendum headings on the latest brief check. INFER: Next checkpoint is the independent ten-case head register result, followed by the sixteen boundary cases; main comparison is prepared and waits only because builds/tests are sequential by instruction.

2026-10-23 05:13:54 UTC — OBSERVED: Unchanged independent head register completed 10 first attempts: 4 pass / 6 fail, 0 errors/skips/retries, with one qualified event and one XML case each; both original A1 cases PASS. OBSERVED: All failing cases belong to the four known inherited groups, pending fresh main confirmation. OBSERVED: Static checkpoint verified local=remote=95d1696dc663159f23773db2416fe37a001a7a31. OBSERVED: Found and corrected an evidence-only descendant-tracking gap in the recovered runner; retained the exact prior runner and explained why max-observed-JVM counts from it were incomplete, without changing product or tests or re-running any test. INFER: The direct entry point still bounds this serial gate structurally to one Mill JVM plus one test JVM; next checkpoint is new sixteen-case probes and eight final A1 cases under corrected process tracking.

2026-10-23 05:15:24 UTC — OBSERVED: Sixteen new head probes complete in 16 first attempts: 14 pass / 2 fail, no errors/skips/retries; corrected descendant recorder observes exactly two JVMs at maximum. OBSERVED: Unknown-first/middle/last names all reject with complete original-field errors; all ten blank-only identity/resume/framing cases and the unknown-name resume case pass. OBSERVED: Only repeated blank final fields hiding an earlier bogus or gzip coding fail, both candidates for inherited A2 pending identical main run. OBSERVED: Previous result checkpoint verified local=remote=cf1dc8dd6d35299d3d2e59b67e29bfd5b7e48799. INFER: Next checkpoint is eight official A1 cases, then a fresh main comparison over the unchanged independent and new probes.

2026-10-23 05:16:06 UTC — OBSERVED: Eight final official A1 cases pass 8/8 in 8 first attempts, zero retries/errors/skips; exact XML/event audit and maximum observed Java count 2 saved. OBSERVED: Boundary checkpoint verified local=remote=3cf9adfb8dd4abaaa953924b1e2f4167ab9818bf. OBSERVED: Main comparison is starting on a new local review/RCOURS4-main branch containing only the three independent test files over origin/main, with no production change. INFER: Next checkpoint is 26-case main XML confirming introduced versus inherited classifications, followed by final findings and process cleanup.

2026-10-23 05:19:40 UTC — OBSERVED: Main comparison completes 26 first attempts: 6 pass / 20 fail, no errors/skips/retries, at most two observed JVMs. OBSERVED: Original review register is 2 pass / 5 fail on main; decoder-ending register 0 pass / 3 fail; blank-member register 4 pass / 12 fail. OBSERVED: All eight head-failing case keys also fail main; no case passes main and fails head. OBSERVED: Both A1 original cases pass both main and final head. OBSERVED: Previous checkpoint verified local=remote=91bb068cab726f9441f87741d165d81327412a97. INFER: A is APPROVE with 0 introduced and 4 inherited groups; B retains 4 inherited coverage gaps; C fails only the literal no-edited-main-tests item already inventoried. INFER: Next checkpoint is final source/remote verification and numeric-PID process cleanup, then scanned evidence push.

## Final verdicts

**A: APPROVE — introduced 0, inherited 4. B: GAPS 4 (all inherited). C: FAIL under the brief's literal no-edited-main-tests requirement; all other named compliance checks pass.**

PR https://github.com/CodexCoder21Organization/coursier/pull/7 remains OPEN at https://github.com/CodexCoder21Organization/coursier/commit/6fa47c3435700763a1d2fc82dd7399d0012184a6 . Main remains https://github.com/CodexCoder21Organization/coursier/commit/f7ac8c67dbe757d9129cfa453a37f2867bd1272d . The first local rebase was a no-op and current main has no new overlapping commits; its buffer-size prerequisite does not supersede the PR. Product branch was not changed or pushed by this review.

### A: introduced-versus-main register

Both original A1 real-server cases now PASS: `Content-Encoding:` with an exact six-byte body and `Content-Encoding: , identity, ,` with the same body. Source is byte-identical to RCOURS3's independent register. The final official eight-case A1 register also passes 8/8 first attempts. The standards reading is recorded in rfc-reading.md, with RFC 9110 sections 5.6.1.2 and 8.4.

New single-field probes `, ,BoGuS`, `bogus, ,`, and `, identity, ,br, ,gzip, ,` all reject with the complete original header and no cached/temporary bytes; unknown names amid blanks cannot resume a prefix either. Blank-only `, , ,` correctly completes a fixed and chunked 206, rejects short fixed/chunked and overlong chunked suffixes, rejects an absent chunk terminator and an invalid range, waits for the closing chunk before publishing, handles an empty artifact, and replaces a prefix with a full 200. All 14 cases pass on the final head.

| Existing group | Current real-server reproduction and wrong behavior | Current head / main | Classification |
|---|---|---|---|
| A2 repeated encoding fields | Two gzip field lines with gzip(gzip(abcdef)) decode only the final field. Unknown then identity is accepted. New variants unknown then blank-only and gzip then blank-only likewise ignore the earlier field; the latter caches gzip bytes. | All four assertions FAIL on both | Inherited header-selection defect; one group |
| A3 later gzip member header | Complete gzip(abc) plus five bytes of the next member header, exact wire Content-Length, publishes only abc. | FAIL / FAIL | Inherited |
| A4 gzip fragment progress | Real sync-flushed abc within one gzip member is held until logger progress; BufferedInputStream requests the next fragment before reporting the first. The fixed 30-second latch gate fails. | FAIL / FAIL | Inherited |
| A5 outer gzip completion | Content-Encoding deflate,gzip with gzip(deflate(abcdef)), corrupt final trailer or four missing trailer bytes with exact wire length, is accepted. Inner EOF is followed by raw framing drain instead of completion of every decoder. | Both assertions FAIL on both | Inherited acceptance; main stores the encoded representation while head decodes it, so this does not claim main already supported stacked decoding |

A2 points to [Downloader.scala:269](https://github.com/CodexCoder21Organization/coursier/blob/6fa47c3435700763a1d2fc82dd7399d0012184a6/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L269): `getContentEncoding` selects one field before list parsing. A3 and A5 point to decoder construction and [the raw finish call](https://github.com/CodexCoder21Organization/coursier/blob/6fa47c3435700763a1d2fc82dd7399d0012184a6/modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala#L361). A4 points to the decoded BufferedInputStream wrapper. Full response definitions, exact assertions, failures and stack traces are retained in the Scala sources and corresponding XML/logs; no console stacks were copied into the report.

The new repeated-blank variants were rejected by the earlier PR parser because of its empty-member bug, but they also fail on main. Against the requested main baseline they are inherited A2, not newly introduced defects. Correctly removing empty members does not erase any nonempty coding within a single field.

### B: test comprehensiveness

Against architecture/TESTING.md, A1 is covered through the public FileCache API with real loopback servers, OS-assigned ports, isolated temporary cache directories, exact full-byte/full-message assertions and final/temporary state checks. The filesystem is real because FileCache takes java.io.File and this review exercises its actual cache publication and persisted-prefix behavior. Faults are forced by exact response bytes or latch ordering, not machine-dependent load. Success, rejection, decoding, prefix handling and ownership/publication are exercised; tests do not call private helpers or use reflection/mocks.

The four remaining missing regression groups in the PR suite are precisely A2–A5 above. The new blank-only combinations do not add an unrelated coverage group: the passing empty-field identity path is the same normalized empty list, while the two failing repeated-field variants belong to A2. B is therefore GAPS 4 overall, all deliberately assigned by the brief to the separate inherited follow-up. This does not turn A's introduced-defect verdict into a request to widen this PR.

### C: compliance and every existing-test edit

Review itself: zero existing test or assertion edits, zero production edits, zero PR changes. Three independent test files were added only on local review branches and copied to this evidence branch.

PR versus main: `DownloadBufferCapacityTests.scala:267` changes existing test `other content encodings retain the default maximum` to `deflate content encoding retains the default maximum`. Before: three raw bytes advertised as custom, expecting those raw bytes and length 3. After: real deflate bytes, expecting the original decoded three bytes and compressed wire length. Exact default-buffer observation, capacity bounds, full-body and stream-close assertions remain. The PR body explicitly inventories and justifies this correction because unsupported names are now rejected; it is not hidden assertion weakening. However it is an edited main test, so the brief's literal C checklist is FAIL. The main and final source copies plus reviewed-diff.patch prove the before/after. A separate zero-length/zero-byte-read case was added without changing another main assertion.

Within the PR-added framing suite, FCOURS5 renames `unknown and empty encodings cannot publish response bytes` to `unknown encodings cannot publish response bytes`; the values custom/br/gzip-custom are unchanged, the empty-string rejection moves to a positive exact-body/publication/cleanup case, and eight A1 cases are added. This corrects the wrong empty-field contract exposed by A1; it is not a main-test edit. No diagnostic golden literals were changed. No main test was deleted, disabled, weakened or given a longer timeout or fewer iterations. Added code contains no reflection or mocks; error messages include the URL and full input/count values, and error assertions retain full text.

### Exact gates and attempt audit

| Gate | PASS | FAIL | First attempts | Retries / errors / skips |
|---|---:|---:|---:|---|
| Final head: unchanged RCOURS3 review + decoder endings | 4 | 6 | 10 | 0 / 0 / 0 |
| Final head: new blank-member boundaries | 14 | 2 | 16 | 0 / 0 / 0 |
| Final head: official final A1 cases | 8 | 0 | 8 | 0 / 0 / 0 |
| Main: identical three independent registers | 6 | 20 | 26 | 0 / 0 / 0 |
| Total case executions | 32 | 28 | 60 | 0 / 0 / 0 |

Each XML testcase has exactly one qualified utest result event; no retry mixin exists, and utest 0.9.1 Executor.utestWrap invokes runBody once. The runner's command-attempt counter was not used as evidence of per-test attempts. The first setup failure had no compiled test or utest event and is preserved separately; it is not counted in these gates or as a test retry. All actual gates finished without a deadline and none exited 137 or -9. No full suite or remote run was requested or run; CI remains with the orchestrator. The direct no-server Mill worker uses at most one build JVM and one test JVM sequentially, requiring no more-than-two-JVM slot; corrected process recording observes a maximum of two in later phases. The earlier recording gap and setup correction are disclosed in tool-challenge.md.

### Brief closure

- A independent register, A1 confirmation and blank-member hunt: DONE, proved by paired XML/events and final-audit.json; introduced 0 and inherited 4.
- B coverage review: DONE, four inherited gaps identified with real-server tests and full evidence.
- C compliance review: DONE, one literal checklist violation documented with exact before/after; other named checks pass.
- Evidence milestones: DONE through scanned pushes; final remote verification and cleanup are recorded below.
- Product changes, merges, queue entries, deployments, artifact publications, check re-requests and handoff-cli: none. No requested review item remains unfinished. A2–A5 are left unchanged because the brief expressly assigns them to another follow-up.

2026-10-23 05:20:44 UTC — OBSERVED: Fresh PR verification is OPEN at the requested 6fa47c3435700763a1d2fc82dd7399d0012184a6; remote main still equals f7ac8c67dbe757d9129cfa453a37f2867bd1272d. OBSERVED: Final attempt audit verifies 60 first attempts, 32 pass / 28 fail across both versions, zero retries/errors/skips and no introduced head-failing case. OBSERVED: Cleanup verified all recorded owned PIDs stopped and inspected the requested ps output in memory, without storing command lines; no process needed termination. OBSERVED: Final brief Addendum check found no headings. INFER: All requested review work is complete; next checkpoint is final scanned evidence push and remote equality verification.

## Verified remote refs before final evidence commit

```text
6fa47c3435700763a1d2fc82dd7399d0012184a6	refs/heads/fix/FCOURS2-download-length-integrity
f7ac8c67dbe757d9129cfa453a37f2867bd1272d	refs/heads/main
ab8909ee9c43b2835ec412959642c86c7768c07b	refs/heads/wip/FCOURS5-evidence-20261023
c3c1ef6e9f0fc5964ed7016e82f615fa099b1b74	refs/heads/wip/RCOURS3-evidence-20261023
91bb068cab726f9441f87741d165d81327412a97	refs/heads/wip/RCOURS4-evidence-20261023
```

OBSERVED: Evidence branch wip/RCOURS4-evidence-20261023 last verified local=remote=91bb068cab726f9441f87741d165d81327412a97 before the final comparison/closure checkpoint. OBSERVED: The final evidence push and its equal local/remote SHA are printed in the final message; each earlier push equality is recorded above.
