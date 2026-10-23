# RC8 review verdicts

Reviewed PR: https://github.com/CodexCoder21Organization/coursier/pull/8

Pinned product head: b2bbbcbbe26c6157cc1a6fbb7b1323c083c08e7c (`fix/FINHCOURS-download-integrity`). Main: 3788263afb4b8977cfd5ebc2a557de566e0c801c. The initial review rebase was a no-op. Main has no newer commits and no equivalent of these fixes; none of this PR is superseded. No PR branch was modified.

## A — APPROVE: zero introduced defects, one remaining inherited defect

No independent probe passed main and failed head. Both changed production files are byte-for-byte the pinned product blobs; `production-manifest.json` records them. All three test sources used for the comparison are byte-identical between worktrees.

**Inherited finding I1: compressed totals and decoded progress use different units.** A real loopback 200 response contains `Content-Encoding: gzip` and `Content-Length: 97`; its complete decoded body is 65,536 copies of `a`. FileCache announces `downloadLength(..., 97, 0, false)` and ends at `downloadProgress(..., 65536)`. The assertion that progress stay within the announced total fails on both main and head, with one attempt each. This can display progress beyond 100%; it does not alter the cached bytes. The mechanism is Downloader.scala:290–291 announcing the encoded response length while :1023 increments the decoded byte count. Both statements already exist on main. A consistent contract must use the same byte unit for total and progress, or leave the decoded total unknown until established.

Reproducer: `coursier.cache.Rc8ReviewTests` test `encoded progress total must describe decoded cumulative positions`, in `modules/cache/jvm/src/test/scala/coursier/cache/Rc8ReviewTests.scala`. First-attempt XML/log evidence: `head-probes-started.xml`, `main-probes.xml`; matched outcomes: `comparison.json`.

| Requested surface | Evidence and outcome |
|---|---|
| Mixed identity, empty list entries and uppercase names | Independent 200 controls pass for identity before/after gzip, uppercase gzip/identity and empty comma entries. Repeated mixed fields also pass the PR's tests. |
| Valid multi-member gzip, including empty members | Leading, middle and trailing empty members with nonempty neighbors pass at buffer sizes 1, 31 and default; PR optional-header and member-integrity tests pass. |
| Deflate inside gzip and gzip inside deflate | Independent controls, PR three-layer controls, and large-stream tests pass. |
| 206 and replacement paths | Saved-prefix identity lists append exactly once; request carries Range bytes=3-; encoded 206 from byte zero works with no prefix; a 200 gzip response replaces a saved prefix. Repeated encoded 206 is rejected and temporary prefix removed. Existing range error and chunked range cases all pass. |
| Rejection cleanup | First-member header/method/flags/body/CRC/size/truncation failures, later-member partial prefixes, optional-header errors, deflate checksum/dictionary/trailing output, outer-trailer errors, unsupported encoding and encoded resume all assert no final/temporary file. Original framing tests cover malformed lengths/ranges, missing closing chunks, no-body statuses, output-open failure and complete original HTTP errors. Source review traces all these branches through the unchanged `!completed` temporary-file deletion finally block. |
| Progress ordering and double counting | Independent tests assert strictly increasing cumulative decoded positions, exactly one known-length event, final decoded length, completion last, and resumed positions including the saved prefix once. Nested and chunked bodies pass; only total units fail, inherited as I1. The PR's fragment latch test fails main and passes head without waiting for the next fragment. |
| Large-body behavior | Each of four tests sends 8 and 64 MiB through real loopback HTTP: gzip, deflate inside gzip, gzip inside deflate, identity. The server withholds every next MiB until the temporary cache file/progress reaches the preceding part, and final files are hashed incrementally. All four tests pass on both versions. The decoder adds only fixed-size per-layer arrays, and no whole-body buffering is introduced. |

Single-run timing measurements for 64 MiB: main/head gzip 2.458/3.277 s; deflate inside gzip 3.352/3.980 s; gzip inside deflate 3.204/4.368 s; identity 0.466/0.486 s. These are observations, not a statistical throughput verdict. Head maximum sampled used heap for the three encoded cases was 38,539,712 /35,513,216 /35,219,192 bytes; sparse samples alone do not prove retained-memory bounds. The causal streaming barriers and bounded buffer code provide the streaming evidence. No tuning flags, artificial load, retries or timing thresholds were used.

The four defects described in the brief are independently confirmed: all six original fail-first tests fail on main and pass head. They are repaired inherited defects and are not counted as remaining findings. The 16 main failures span these fixes and their adjacent controls; they are not sixteen distinct remaining defects.

## B — GAPS 2

Read against https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/architecture/TESTING.md, particularly TDD, hermetic public-API tests, comprehensive edge/error/scale coverage, no machine-calibrated reproduction and full error assertions.

The PR has strong deterministic regression coverage: real ephemeral loopback servers, public FileCache calls, isolated temporary cache directories removed in finally, explicit no-retry configuration, exact error text, both cache-path absence checks, member-prefix cutoffs, optional gzip header checksums, one-byte buffers, nesting order and synchronized progress/publication state. The new tests read no ambient environment and use no mocks or reflection. On-disk temporary storage is necessary for the public FileCache(java.io.File) persistence/resume contract; each invocation owns and removes its own directory. The Kompile-specific .kts layout rules do not apply to this Scala/Mill project. The prior evidence's 30 rounds and cache-module gate were read, not rerun or counted as this review's attempts.

1. **B1 — no consistent total/progress contract assertion.** The PR logger records progress but its downloadLength implementation only releases a watcher latch; it does not retain totals or assert their relation to cumulative decoded counts. I1 therefore passes the PR suite. Keep a public callback test for known encoded lengths, with exact event ordering, consistent units, correct resume offsets and no duplicate positions. This review's I1 probe fails on head and main.
2. **B2 — no permanent large encoded streaming gate.** The PR's payload is six bytes; the existing gzip buffer-capacity control is also tiny. Those tests cannot catch a future whole-body decoded buffer. Keep a large generated-body loopback test that verifies file progress while the server withholds later bytes and hashes the final file without reading it all into memory. The four review-only streaming tests provide this missing coverage and pass; they have not been added to the PR because edits are prohibited.

No missing test is claimed to show a new product defect unless its real-server reproduction fails head while passing main. The missing permanent scale gate is a test gap, not an observed buffering bug.

## C — PASS

The pinned diff introduces no reflection, mocks, Mockito, public-for-test API, disabled/skipped tests, retries, timeout increases or iteration reductions. It adds one test file and edits no test on main. `compliance.json` records the path/token/diff audit. Errors exposed by the new decoder are wrapped with Content-Encoding, artifact URL, expected complete decoding, received response-byte count and a cause such as a corrupt trailer; deflate dictionary/trailing-data errors also state the expected state. Tests protect full errors. README behavior matches decoding order, every-member/layer verification and prompt progress.

Existing-test edit inventory for this review: none. PR existing-test edit inventory: none. The prior FINHCOURS new resume-fixture correction is recorded in that branch's findings and predates this review; no such edit was made here.

## Gates and attempts

| Phase | First-attempt pass/fail | Retries | Maximum observed JVMs |
|---|---:|---:|---:|
| Head: PR 16 plus independent 8 | 23 /1 | 0 | 2 |
| Head: four streaming tests (each 8 and 64 MiB) | 4 /0 | 0 | 2 |
| Head: original framing register | 103 /0 | 0 | 2 |
| Main: same PR 16, independent 8, streaming 4 | 12 /16 | 0 | 2 |
| Total | 142 /17 (159 attempts) | 0 | 2 |

Every test has exactly one XML case and one named result event. Tests ran sequentially through Mill 0.12.15 no-server -j1, with no remote submissions. One initial launcher invocation failed before any test because out/mill-lock's parent did not exist; creating out fixed the launcher prerequisite. That invocation is separately retained in head-probes.log/result and contributes zero test attempts. No failing test was retried unchanged. No exit 137 occurred.

Reproduction: clone product https://github.com/CodexCoder21Organization/coursier at b2bbbcbbe26c6157cc1a6fbb7b1323c083c08e7c into a fresh directory, then copy the three Scala test snapshots from this evidence branch's `modules/cache/jvm/src/test/scala/coursier/cache/` into that product checkout. From the product root, run `python3 /path/to/evidence/RC8/run-command.py "$PWD" reproduce python3 /path/to/evidence/RC8/mill-direct.py --no-server -j1 'cache.jvm[2.13.16].test.testOnly' coursier.cache.Rc8ReviewTests`. The launcher performs the required main rebase first; at review time this leaves the pinned product unchanged. This runs the eight independent scenarios once. A main comparison uses a fresh origin/main worktree with the identical three added test sources copied into the same package. The final evidence branch is deliberately an evidence-only root commit and is not itself a buildable product checkout.

The runner records free memory before each test command, tracks owned PID/start-time pairs rather than command-line matching, caps targeted waits and checks exact attempts. Scans ran before every earlier evidence push, but the shell did not stop on an existing fixture-expression match; see the final scan correction below. Raw environment/process dumps and build outputs are excluded.

Review execution is complete; no requested analysis or targeted gate remains unfinished. I1 and B1/B2 are reported for the owner to act on, and are deliberately not fixed in this review. No full workspace/cache-module rerun or CI monitoring was requested as a fresh gate; prior cache-module results remain on FINHCOURS evidence. No merge, enqueue, deployment, artifact publication, check re-request, remote run, PR comment or handoff command occurred.


Cleanup: 19 recorded PID/start pairs checked; zero owned live processes remain; prescribed ps audit completed without storing command lines.


## Final scan correction

The full product tree contains a pre-existing assignment in `modules/cache/jvm/src/test/resources/generate-keystore.sh:6`, referencing the `SSL_PASSWORD` variable with a fallback. It is byte-identical to main and was not added or evaluated by this review. The required scan matched that expression on the earlier checkpoints. Those commands continued because the shell did not stop when the check failed, so earlier statements calling the scans clean were incorrect. No environment values or newly introduced credentials were recorded. The final evidence branch has been replaced with an evidence-only root commit, containing the complete review, logs and three test snapshots, so the unrelated fixture is absent and the required whole-tree scan can pass without deleting or editing any product test. Earlier evidence checkpoint SHAs remain recorded as history; this correction is explicit rather than silently removing the match. No PR branch or product test was changed.

RC8 · A: APPROVE (introduced 0, inherited 1) · B: GAPS 2 · C: PASS · head b2bbbcbb
