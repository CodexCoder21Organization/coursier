# RC9 combined review

OBSERVED — Review started 2026-11-27 04:35 UTC. Scope: https://github.com/CodexCoder21Organization/coursier/pull/9. No code changes or writes to the pull request are authorized. Notes will be copied under evidence/RC9/ on wip/RC9-evidence-20261127.

OBSERVED — Plan:
- IN PROGRESS: locate and freshly clone coursier, build-kotlin-jvm, and DocumentationRepository; read root instructions and required review documents in full.
- PENDING: inspect the entire touched files and all coordination-file paths; trace download outcomes, abrupt process exit, directory readers, and structure coordination.
- PENDING: assess deterministic test ordering, exact listing changes, and returned-byte assertions; optionally run one targeted test.
- PENDING: read author record last; check live pull request checks and required checks once; write verdict and push final evidence.

OBSERVED — 04:38 UTC checkpoint: freshly cloned all three repositories under workspace/. No AGENTS.md files were found in them. Required Code Review Red Flags and State Management read end to end; Testing read through end, with one output-truncated paragraph being re-read separately. Read fork README; consumer README being completed. Fork PR head is f11bef695a648b6cf43fc90969e72887411f2f96, OPEN; base snapshot 119e39d99. First evidence checkpoint pushed: https://github.com/CodexCoder21Organization/coursier/commit/92064a59b.

OBSERVED — Read all four touched files in full, including the complete 299-line FileCacheTests.scala. Its only diff is five added expected empty coordination-file entries across three exact listing assertions (two module entries, three POM entries); existing content expectations and exact sorted sequence comparison are unchanged.

OBSERVED — CacheLocks.withLockOr opens the coordination file once before tryLock looping; PR removes only deletion of its name after release/close, plus changes explanatory comment/import. File-based structure coordination still releases/closes/deletes; Path-based overload uses DELETE_ON_CLOSE. All call sites are being traced.

INFERRED — The mechanism is removal of a coordination pathname while another process retains its open channel, and the evidence that shows it is the held-channel loop in CacheLocks plus KLOCK3's observed missing pathname and overlapping downloads. Keeping the pathname stable addresses that mechanism without changing production waits or polling.

OBSERVED — Literal count qualification: FileCacheTests now expects foo.module.lock even when the module GET returns not-found. Therefore leftover files are bounded by distinct attempted coordinated paths, not by only successfully downloaded files. This is not yet a build-result defect; review of cleanup and readers continues.

OBSERVED — Completed full reads of Code Review Red Flags, Testing, State Management, Engineering Philosophy, the three repository READMEs, and When a Fix Does Not Fix Production. Documentation snapshot: https://github.com/CodexCoder21Organization/DocumentationRepository/commit/6acfb5b1d331c7dde0cebea8beda563b0d6420b6. Consumer snapshot: https://github.com/CodexCoder21Organization/build-kotlin-jvm/commit/39dead1775f7dc325e74aa9b42f993cea2433939.

OBSERVED — Current build-kotlin-jvm uses a repository-list namespace under the Coursier root (`kompile-repository-order-v1/<hash>`, JvmBuildRules.kts:1941), rather than only the root described by the original reproduction. Callers with the same ordered repositories still share each cache file. Its closure materializer copies only explicitly returned JAR and POM files into fresh artifact directories, not their siblings; it does not copy Coursier coordination files. Its own locks are in a separate resolvedClosures/locks directory and remain in place already.

INFERRED — Test follow-up: awaiting B's GET does not establish that A has finished the release/close/delete sequence on the old code. If A is paused after release but before deletion, B starts GET and C can open the still-named original file; this test can pass on old code for that ordering. Await A's exit after B's GET and before starting C to make the old unlink finish through an observable process boundary. This is a coverage observation, not a demonstrated production defect in the fix.
