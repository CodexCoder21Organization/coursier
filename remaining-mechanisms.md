# Remaining gate failures

The fixed-cache gate completed all 450 cases: 446 passed, 4 failed, 450 first attempts, zero retries. The previous run was444 passed / 6 failed. The test source hashes, 30 iterations, four workers, two-CPU affinity and attempt limit match. This run also has scratch repository wiring and a fresh build cache, so the difference of two failures does not establish a causal improvement from buffer sizing alone.

The outer manager contains the five fixed download classes. Three generated Maven fixture archives embed the exact snapshot jar, and all three resolved buffer3 jars in the gate cache have the same SHA256. See `final-snapshot-binding.json`, `manager-binding.json`, `fixture-byte-bindings.json` and `gate-resolved-cache-bindings.json`.

| Case | Failed iterations | Direct evidence |
|---|---|---|
| RepeatedPublicBuildCopies |2,4|31 and 32 serial rule JVM starts; compiler results are cache hits; recorded lock waits are short.|
| JoinedWorkload |2,16|Parent waits for nested test completion; the handoff receiver executor is idle. The original nested Future exception was not recorded.|

## Repeated public copies

The fixture invokes `fixture.decoded.result("same")`32 times. `buildTopLevelRuleThroughResultIndex` in core `src/Workspace.kt` only indexes zero-argument rules. This parameterized invocation therefore calls `buildInternal` for every public call, starting another rule JVM even when compilation is cached. Iteration 2 records31 starts and30 completion notices; iteration 4 records32 starts and32 notices before its deadline result. The last starts occur30.801 and 30.258 seconds after body start. This directly establishes repeated uncached rule-process execution across the 30-second body budget. The maximum recorded core lock wait is 9.495 ms; these records do not support a long lock-wait explanation. They do not establish which startup substep accounts for every millisecond. See `repeated-copy-mechanism-review.md` and `gate-results/failure-event-projection.json`.

## Joined workload

The readiness listener submits a receiver that calls `handoff.accept()` with the existing 10-second socket setting. The child first performs compiler and nested-workspace operations, then connects and waits for one reply byte. The parent only reads the receiver Future after nested execution returns. If the accept task expires before that connection, the listening socket stays open, the late connection succeeds, and no receiver remains to send the byte. The child waits for the reply while the parent waits for the child.

A separate diagnostic copy forwards inner events and reads the public Future state without changing assertions or timeouts. It passes alone in 9624 ms. A second copy forces the exact ordering through the public readiness callback: it waits for that existing accept Future to fail before returning and releasing the child. The Future reports `SocketTimeoutException`; no accept-return marker occurs. At 20 and 25 seconds, derived child frames show `testProbe.kt:91`, the unchanged `control.getInputStream().read()` call. The parent then fails at 32190 ms. A separate real-loopback socket experiment forces accept expiry before connecting and confirms the missing reply 10/10 times. See `joined-late-handoff`, `expired-handoff-result.json` and the diagnostic source/patch.

This is a concrete reproduction of the handoff failure and matches the parent/idle-receiver states in both natural gate failures. It is not conclusive recovery of those original Future exceptions: the original reports did not retain them, and their processes have exited. Attribution of the two natural JoinedWorkload failures to this reproduced ordering remains an inference. No buffer cause is assigned to them, and no consumer fix is included in this buffer-sizing PR.
