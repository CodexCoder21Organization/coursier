# Gate count and claim review

Scope: parsed the saved XML/JSON result registers and `pr-description.md` under this evidence directory. No builds, tests, source edits, or external calls were made. The parent supplied the product head (`2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8`) and branch state; I did not query remotes.

## Result counts

| Register | Runs | Cases per run | Observed result | Skips, errors, duplicate cases |
| --- | ---: | ---: | --- | --- |
| `before-final-{01..10}` | 10 | 12 | Seven size cases failed all 10 runs; five controls passed all 10 | 0, 0, 0 |
| `after-final-{01..30}` | 30 | 12 | All 12 cases passed all 30 runs | 0, 0, 0 |
| `before-identity-{01..10}` | 10 | 13 | Mixed-case identity failed all 10 runs; the other 12 passed all 10 | 0, 0, 0 |
| `after-identity-{01..30}` | 30 | 13 | All 13 cases passed all 30 runs | 0, 0, 0 |
| `cache-suite.xml` | 1 saved invocation | 104 | 104 unique cases passed | 0, 0, 0 |

The seven baseline failures are the tiny, 199-byte POM, one-byte, lowercase identity, default-boundary, empty-body, and persisted-prefix Range cases. The five baseline controls are other encoding, over-cap body, unknown chunked length, unknown identity length, and gzip decoding.

For each repeated group, JSON iterations are numbered 1 through N and contain exactly 12 or 13 distinct cases each. Their per-case totals agree with the summary JSON and matching XML testcase results. The summaries record one attempt per case per run. XML testcase attributes contain only `classname`, `name`, and `time`; there are no skipped, disabled, flaky, rerun, retry, or duplicate-case markers. XML root totals agree with testcase contents. Exit codes are 1 for every expected-red baseline run and 0 for every green run.

The cache-module register is one JUnit XML invocation dated `2026-10-22T19:11:47`, before the mixed-case correction evidence. It contains 104 unique testcases, all passing. This agrees with the description, which says the module suite ran once before that review correction.

## Consumer preflight and description discrepancy

`consumer-first.xml` has zero testcases. Its JSON register has one unsuccessful compile result with duration zero; cleanup reports no remaining workers. This is a compile preflight failure, not a test-body failure or an acceptance-gate result. The provided evidence contains no downstream acceptance-gate result.

The last paragraph of `pr-description.md` says the downstream thirty-iteration acceptance measurement “is in progress.” That wording does not match the saved consumer evidence or the task state supplied for this audit: the preflight failed before tests ran, the generated source was then corrected, and the acceptance gate has not started. Update that status line to say the gate is pending; make no claim about its future result. The local evidence files do not verify whether the waiting controller later starts.

The other numerical claims in the PR description match the registers: seven baseline failures at 10/10, the added mixed-case case failing 10/10 before the case-insensitive comparison, thirteen cases passing 30/30 after it, and 104/104 cache-module tests once before the correction. “Each case ran once per run with no retries” matches the one-attempt summary, one testcase per case per iteration, and absence of retry markers.

## Documentation read in the earlier review

- Full `PHILOSOPHY.md` copy: `PHILOSOPHY-read.md`.
- Full `architecture/TESTING.md` copy: `TESTING-read.md`.
- Coursier project `README.md` (read in the earlier code/test review).

No documents were re-fetched or re-read for this mechanical audit.
